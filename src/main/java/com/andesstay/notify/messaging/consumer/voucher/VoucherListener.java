package com.andesstay.notify.messaging.consumer.voucher;

import com.andesstay.notify.event.ReservationEnvelope;
import com.andesstay.notify.messaging.retry.RetryPublisher;
import com.andesstay.notify.services.IdempotencyRegistry;
import com.andesstay.notify.services.VoucherService;
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
 * Listener de la cola de voucher ({@code q.cmd.voucher}).
 * <p>
 * ┌──────────────────────────────────────────────────────────────────────────┐
 * │  LÓGICA DE ACK / NACK                                                    │
 * │                                                                          │
 * │  1. DUPLICADO (idempotencia):                                            │
 * │     → basicAck silencioso. Voucher ya generado anteriormente.            │
 * │                                                                          │
 * │  2. PROCESAMIENTO EXITOSO:                                               │
 * │     → basicAck. Voucher generado; mensaje eliminado de la cola.          │
 * │                                                                          │
 * │  3. ERROR RECUPERABLE (ej. generador de PDF temporalmente no disponible):│
 * │     → republica con x-retry-count+1 si quedan reintentos. │
 * │     → basicNack(requeue=false) si se agotaron → DLQ (q.cmd.voucher.dlq).│
 * │                                                                          │
 * │  4. ERROR NO RECUPERABLE (datos de reserva inválidos o incompletos       │
 * │     para generar el PDF):                                                │
 * │     → basicNack(requeue=false) directo a q.cmd.voucher.dlq.              │
 * └──────────────────────────────────────────────────────────────────────────┘
 */
@Component
public class VoucherListener {

    private static final Logger log = LoggerFactory.getLogger(VoucherListener.class);

    private final VoucherService voucherService;
    private final IdempotencyRegistry idempotencyRegistry;
    private final int maxRetry;
    private final String queueName;
    private final RetryPublisher retryPublisher;

    public VoucherListener(
            VoucherService voucherService,
            IdempotencyRegistry idempotencyRegistry,
            com.andesstay.notify.config.RabbitProperties rabbitProperties,
            RetryPublisher retryPublisher) {
        this.voucherService = voucherService;
        this.idempotencyRegistry = idempotencyRegistry;
        this.maxRetry = rabbitProperties.maxRetryAttempts();
        this.queueName = rabbitProperties.voucher().queue();
        this.retryPublisher = retryPublisher;
    }

    /**
     * Consume mensajes de {@code q.cmd.voucher} con ACK manual.
     *
     * @param envelope   Envelope deserializado desde JSON.
     * @param channel    Canal AMQP para ACK/NACK.
     * @param tag        Delivery tag del mensaje.
     * @param retryCount Número de reintentos previos.
     */
    @RabbitListener(queues = "${app.rabbit.voucher.queue}")
    public void onVoucherMessage(
            ReservationEnvelope envelope,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long tag,
            @Header(value = RetryPublisher.RETRY_HEADER, defaultValue = "0") int retryCount)
            throws IOException {

        // ── Validación de envelope ────────────────────────────────────────────
        if (envelope == null || envelope.eventId() == null || envelope.payload() == null) {
            log.error("[VOUCHER] Mensaje con envelope o payload nulo; enviando a DLQ. tag={}", tag);
            channel.basicNack(tag, false, false);
            return;
        }

        // ── Idempotencia + procesamiento ──────────────────────────────────────
        // La consulta de idempotencia va DENTRO del try: si la BD falla es un error
        // recuperable (reintento limitado y luego DLQ) y no un bucle infinito.
        try {
            if (idempotencyRegistry.alreadyProcessed(envelope.eventId())) {
                log.warn("[VOUCHER] Duplicado detectado, ignorando. eventId={}", envelope.eventId());
                channel.basicAck(tag, false); // ACK silencioso; no reprocesar
                return;
            }

            log.info("[VOUCHER] Procesando evento. eventId={}, tipo={}",
                    envelope.eventId(), envelope.type());

            voucherService.generateVoucherPdf(envelope);

            idempotencyRegistry.markAsProcessed(envelope.eventId());
            channel.basicAck(tag, false);
            log.info("[VOUCHER] Mensaje procesado exitosamente. eventId={}", envelope.eventId());

        } catch (IllegalArgumentException | NullPointerException e) {
            // ── Error NO RECUPERABLE: datos inválidos o faltantes ────────────
            // basicNack con requeue=false: el broker lo envía al DLX → DLQ
            log.error("[VOUCHER] Error no recuperable (datos inválidos). eventId={}, error={}",
                    envelope.eventId(), e.getMessage(), e);
            channel.basicNack(tag, false, false); // → DLQ

        } catch (Exception e) {
            // ── Error RECUPERABLE: fallo temporal ────────────────────────────
            if (retryCount < maxRetry) {
                log.warn("[VOUCHER] Error recuperable, reintentando ({}/{}). eventId={}, error={}",
                        retryCount + 1, maxRetry, envelope.eventId(), e.getMessage());
                try {
                    // Se republica con x-retry-count + 1 y se confirma el original.
                    retryPublisher.republish(queueName, envelope, retryCount);
                    channel.basicAck(tag, false);
                } catch (AmqpException publishError) {
                    // Si no se pudo reencolar, no se pierde el mensaje: va a la DLQ.
                    log.error("[VOUCHER] No se pudo republicar para reintento; enviando a DLQ. eventId={}, error={}",
                            envelope.eventId(), publishError.getMessage(), publishError);
                    channel.basicNack(tag, false, false); // → DLQ
                }
            } else {
                log.error("[VOUCHER] Reintentos agotados ({}). Enviando a DLQ. eventId={}, error={}",
                        maxRetry, envelope.eventId(), e.getMessage(), e);
                channel.basicNack(tag, false, false); // requeue=false → DLQ
            }
        }
    }
}
