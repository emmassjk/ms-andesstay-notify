package com.andesstay.notify.messaging.consumer.housekeeping;

import com.andesstay.notify.entities.Notificacion.CanalNotificacion;
import com.andesstay.notify.event.ReservationEnvelope;
import com.andesstay.notify.services.HousekeepingService;
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
 * Listener de la cola de housekeeping ({@code q.cmd.housekeeping}).
 * <p>
 * ┌──────────────────────────────────────────────────────────────────────────┐
 * │  LÓGICA DE ACK / NACK                                                    │
 * │                                                                          │
 * │  1. DUPLICADO (idempotencia):                                            │
 * │     → basicAck silencioso.                                               │
 * │                                                                          │
 * │  2. PROCESAMIENTO EXITOSO:                                               │
 * │     → basicAck. El mensaje es eliminado de la cola.                      │
 * │                                                                          │
 * │  3. ERROR RECUPERABLE (ej. sistema de tickets temporalmente caído):      │
 * │     → republica con x-retry-count+1 si quedan reintentos. │
 * │     → basicNack(requeue=false) si se agotaron → va a DLQ.               │
 * │                                                                          │
 * │  4. ERROR NO RECUPERABLE (datos inválidos, campos obligatorios nulos):   │
 * │     → basicNack(requeue=false) directo a q.cmd.housekeeping.dlq.         │
 * └──────────────────────────────────────────────────────────────────────────┘
 */
@Component
public class HousekeepingListener {

    private static final Logger log = LoggerFactory.getLogger(HousekeepingListener.class);

    private final HousekeepingService housekeepingService;
    private final IdempotencyRegistry idempotencyRegistry;
    private final NotificacionService notificacionService;
    private final int maxRetry;
    private final String queueName;
    private final RetryPublisher retryPublisher;

    public HousekeepingListener(
            HousekeepingService housekeepingService,
            IdempotencyRegistry idempotencyRegistry,
            NotificacionService notificacionService,
            com.andesstay.notify.config.RabbitProperties rabbitProperties,
            RetryPublisher retryPublisher) {
        this.housekeepingService = housekeepingService;
        this.idempotencyRegistry = idempotencyRegistry;
        this.notificacionService = notificacionService;
        this.maxRetry = rabbitProperties.maxRetryAttempts();
        this.queueName = rabbitProperties.housekeeping().queue();
        this.retryPublisher = retryPublisher;
    }

    /**
     * Consume mensajes de {@code q.cmd.housekeeping} con ACK manual.
     *
     * @param envelope   Envelope deserializado desde JSON.
     * @param channel    Canal AMQP para ACK/NACK.
     * @param tag        Delivery tag del mensaje.
     * @param retryCount Número de reintentos previos (0 si es la primera entrega).
     */
    @RabbitListener(queues = "${app.rabbit.housekeeping.queue}")
    public void onHousekeepingMessage(
            ReservationEnvelope envelope,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long tag,
            @Header(value = RetryPublisher.RETRY_HEADER, defaultValue = "0") int retryCount)
            throws IOException {

        // ── Validación de envelope ────────────────────────────────────────────
        if (envelope == null || envelope.eventId() == null || envelope.payload() == null) {
            log.error("[HOUSEKEEPING] Mensaje con envelope o payload nulo; enviando a DLQ. tag={}", tag);
            channel.basicNack(tag, false, false);
            return;
        }

        // ── Idempotencia + procesamiento ──────────────────────────────────────
        // La consulta de idempotencia va DENTRO del try: si la BD falla es un error
        // recuperable (reintento limitado y luego DLQ) y no un bucle infinito.
        try {
            if (idempotencyRegistry.alreadyProcessed(envelope.eventId())) {
                log.warn("[HOUSEKEEPING] Duplicado detectado, ignorando. eventId={}", envelope.eventId());
                channel.basicAck(tag, false); // ACK silencioso; no reprocesar
                return;
            }

            log.info("[HOUSEKEEPING] Procesando evento. eventId={}, tipo={}",
                    envelope.eventId(), envelope.type());

            housekeepingService.createHousekeepingTicket(envelope);

            idempotencyRegistry.markAsProcessed(envelope.eventId());
            notificacionService.registrarNotificacionExitosa(
                    CanalNotificacion.HOUSEKEEPING, envelope,
                    "Ticket housekeeping para habitación " + envelope.payload().roomNumber(),
                    "Ticket generado para " + envelope.payload().propertyName());
            channel.basicAck(tag, false);
            log.info("[HOUSEKEEPING] Mensaje procesado exitosamente. eventId={}", envelope.eventId());

        } catch (IllegalArgumentException | NullPointerException e) {
            // ── Error NO RECUPERABLE: datos inválidos o faltantes ────────────
            // basicNack con requeue=false: el broker lo envía al DLX → DLQ
            log.error("[HOUSEKEEPING] Error no recuperable (datos inválidos). eventId={}, error={}",
                    envelope.eventId(), e.getMessage(), e);
            notificacionService.registrarNotificacionFallida(CanalNotificacion.HOUSEKEEPING, envelope, e.getMessage());
            channel.basicNack(tag, false, false); // → DLQ

        } catch (Exception e) {
            // ── Error RECUPERABLE: fallo temporal ────────────────────────────
            if (retryCount < maxRetry) {
                log.warn("[HOUSEKEEPING] Error recuperable, reintentando ({}/{}). eventId={}, error={}",
                        retryCount + 1, maxRetry, envelope.eventId(), e.getMessage());
                try {
                    // Se republica con x-retry-count + 1 y se confirma el original.
                    retryPublisher.republish(queueName, envelope, retryCount);
                    channel.basicAck(tag, false);
                } catch (AmqpException publishError) {
                    // Si no se pudo reencolar, no se pierde el mensaje: va a la DLQ.
                    log.error("[HOUSEKEEPING] No se pudo republicar para reintento; enviando a DLQ. eventId={}, error={}",
                            envelope.eventId(), publishError.getMessage(), publishError);
                    channel.basicNack(tag, false, false); // → DLQ
                }
            } else {
                log.error("[HOUSEKEEPING] Reintentos agotados ({}). Enviando a DLQ. eventId={}, error={}",
                        maxRetry, envelope.eventId(), e.getMessage(), e);
                channel.basicNack(tag, false, false); // requeue=false → DLQ
            }
        }
    }
}
