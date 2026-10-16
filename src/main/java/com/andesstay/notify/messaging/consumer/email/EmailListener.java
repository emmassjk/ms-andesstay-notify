package com.andesstay.notify.messaging.consumer.email;

import com.andesstay.notify.entities.Notificacion.CanalNotificacion;
import com.andesstay.notify.event.ReservationEnvelope;
import com.andesstay.notify.services.EmailNotificationService;
import com.andesstay.notify.services.NotificacionService;
import com.andesstay.notify.messaging.retry.RetryPublisher;
import com.andesstay.notify.services.IdempotencyRegistry;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Listener de la cola de email ({@code q.cmd.email}).
 * <p>
 * ┌──────────────────────────────────────────────────────────────────────────┐
 * │  LÓGICA DE ACK / NACK                                                    │
 * │                                                                          │
 * │  1. DUPLICADO (idempotencia):                                            │
 * │     → basicAck silencioso. El mensaje ya fue procesado; no hay nada más  │
 * │       que hacer. Evita procesar dos veces el mismo evento.               │
 * │                                                                          │
 * │  2. PROCESAMIENTO EXITOSO:                                               │
 * │     → basicAck. El broker elimina el mensaje de la cola.                 │
 * │                                                                          │
 * │  3. ERROR RECUPERABLE (IOException, timeout, servicio temporalmente      │
 * │     no disponible):                                                      │
 * │     → El intento actual viaja en la cabecera x-retry-count.              │
 * │     → Si quedan reintentos: RetryPublisher republica el mensaje con      │
 * │       x-retry-count + 1 y se hace basicAck del original.                 │
 * │     → Si se agotaron reintentos: basicNack(requeue=false) → el broker    │
 * │       enruta el mensaje al DLX (cmd.dead.dlx) → llega a la DLQ          │
 * │       (q.cmd.email.dlq).                                                 │
 * │                                                                          │
 * │  4. ERROR NO RECUPERABLE (JSON inválido, datos faltantes, NPE en        │
 * │     payload, ClassCastException):                                        │
 * │     → basicNack(requeue=false) directo a DLQ. No tiene sentido reinten- │
 * │       tar; el mensaje nunca podrá procesarse correctamente con los datos │
 * │       que trae.                                                          │
 * └──────────────────────────────────────────────────────────────────────────┘
 */
@Component
public class EmailListener {

    private static final Logger log = LoggerFactory.getLogger(EmailListener.class);

    private final EmailNotificationService emailService;
    private final IdempotencyRegistry idempotencyRegistry;
    private final NotificacionService notificacionService;
    private final int maxRetry;
    private final String queueName;
    private final RetryPublisher retryPublisher;

    public EmailListener(
            EmailNotificationService emailService,
            IdempotencyRegistry idempotencyRegistry,
            NotificacionService notificacionService,
            com.andesstay.notify.config.RabbitProperties rabbitProperties,
            RetryPublisher retryPublisher) {
        this.emailService = emailService;
        this.idempotencyRegistry = idempotencyRegistry;
        this.notificacionService = notificacionService;
        this.maxRetry = rabbitProperties.maxRetryAttempts();
        this.queueName = rabbitProperties.email().queue();
        this.retryPublisher = retryPublisher;
    }

    /**
     * Consume mensajes de {@code q.cmd.email} con ACK manual.
     *
     * @param envelope Envelope deserializado desde JSON por Jackson.
     * @param channel  Canal AMQP para controlar el ACK manualmente.
     * @param tag      Delivery tag necesario para basicAck/basicNack.
     * @param retryCount Número de veces que este mensaje ya fue reentregado
     *                   (Spring AMQP lo expone como cabecera {@code x-death} count,
     *                   pero aquí usamos la cabecera estándar de redelivery).
     */
    @RabbitListener(queues = "${app.rabbit.email.queue}")
    public void onEmailMessage(
            ReservationEnvelope envelope,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long tag,
            @Header(value = RetryPublisher.RETRY_HEADER, defaultValue = "0") int retryCount)
            throws IOException {

        // ── Validación de envelope ────────────────────────────────────────────
        // Error no recuperable: payload nulo o eventId ausente → DLQ inmediata
        if (envelope == null || envelope.eventId() == null || envelope.payload() == null) {
            log.error("[EMAIL] Mensaje con envelope o payload nulo; enviando a DLQ. tag={}", tag);
            channel.basicNack(tag, false, false); // requeue=false → DLQ
            return;
        }

        // ── Idempotencia + procesamiento ──────────────────────────────────────
        // La consulta de idempotencia va DENTRO del try: si la BD falla es un error
        // recuperable (reintento limitado y luego DLQ) y no un bucle infinito.
        try {
            if (idempotencyRegistry.alreadyProcessed(envelope.eventId())) {
                log.warn("[EMAIL] Duplicado detectado, ignorando. eventId={}", envelope.eventId());
                channel.basicAck(tag, false); // ACK silencioso; no reprocesar
                return;
            }

            log.info("[EMAIL] Procesando evento. eventId={}, tipo={}",
                    envelope.eventId(), envelope.type());

            emailService.sendReservationConfirmation(envelope);

            idempotencyRegistry.markAsProcessed(envelope.eventId());
            notificacionService.registrarNotificacionExitosa(
                    CanalNotificacion.EMAIL, envelope,
                    "Confirmación de reserva #" + envelope.payload().reservationId(),
                    "Email enviado a " + envelope.payload().guestEmail());
            channel.basicAck(tag, false);
            log.info("[EMAIL] Mensaje procesado exitosamente. eventId={}", envelope.eventId());

        } catch (IllegalArgumentException | NullPointerException e) {
            // ── Error NO RECUPERABLE: datos inválidos o faltantes ────────────
            // basicNack con requeue=false: el broker lo envía al DLX → DLQ
            log.error("[EMAIL] Error no recuperable (datos inválidos). eventId={}, error={}",
                    envelope.eventId(), e.getMessage(), e);
            notificacionService.registrarNotificacionFallida(CanalNotificacion.EMAIL, envelope, e.getMessage());
            channel.basicNack(tag, false, false); // → DLQ

        } catch (Exception e) {
            // ── Error RECUPERABLE: fallo temporal ────────────────────────────
            if (retryCount < maxRetry) {
                log.warn("[EMAIL] Error recuperable, reintentando ({}/{}). eventId={}, error={}",
                        retryCount + 1, maxRetry, envelope.eventId(), e.getMessage());
                try {
                    // Se republica con x-retry-count + 1 y se confirma el original.
                    retryPublisher.republish(queueName, envelope, retryCount);
                    channel.basicAck(tag, false);
                } catch (AmqpException publishError) {
                    // Si no se pudo reencolar, no se pierde el mensaje: va a la DLQ.
                    log.error("[EMAIL] No se pudo republicar para reintento; enviando a DLQ. eventId={}, error={}",
                            envelope.eventId(), publishError.getMessage(), publishError);
                    channel.basicNack(tag, false, false); // → DLQ
                }
            } else {
                log.error("[EMAIL] Reintentos agotados ({}). Enviando a DLQ. eventId={}, error={}",
                        maxRetry, envelope.eventId(), e.getMessage(), e);
                channel.basicNack(tag, false, false); // requeue=false → DLQ
            }
        }
    }
}
