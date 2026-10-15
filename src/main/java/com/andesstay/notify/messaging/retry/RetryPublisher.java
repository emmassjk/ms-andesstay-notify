package com.andesstay.notify.messaging.retry;

import com.andesstay.notify.event.ReservationEnvelope;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Republica un mensaje en su misma cola con el contador de reintentos incrementado.
 * <p>
 * <strong>Por qué existe:</strong> un {@code basicNack(requeue=true)} devuelve el mismo
 * mensaje a la cola sin modificar sus cabeceras, así que el contador de reintentos
 * nunca avanzaba y un error recuperable se reintentaba infinitamente. Aquí se
 * publica una copia con {@value #RETRY_HEADER} + 1 y el listener hace
 * {@code basicAck} del original. Cuando el contador llega al máximo, el listener
 * ya no reintenta y hace {@code basicNack(requeue=false)} → DLX → DLQ.
 * <p>
 * Se publica por el exchange por defecto ("") usando el nombre de la cola como
 * routing key, de modo que el mensaje vuelve solo a la cola que lo falló (no a
 * las otras colas enlazadas al mismo exchange).
 */
@Component
public class RetryPublisher {

    /** Cabecera AMQP que lleva la cantidad de reintentos realizados. */
    public static final String RETRY_HEADER = "x-retry-count";

    /** Exchange por defecto de RabbitMQ: enruta directo a la cola con ese nombre. */
    private static final String DEFAULT_EXCHANGE = "";

    private final RabbitTemplate rabbitTemplate;

    public RetryPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Vuelve a encolar el mensaje con el contador incrementado.
     *
     * @param queueName         cola de destino (la misma de la que se consumió).
     * @param envelope          envelope original.
     * @param currentRetryCount reintentos ya realizados antes de este fallo.
     * @throws org.springframework.amqp.AmqpException si no se pudo publicar.
     */
    public void republish(String queueName, ReservationEnvelope envelope, int currentRetryCount) {
        int nextRetryCount = currentRetryCount + 1;
        rabbitTemplate.convertAndSend(DEFAULT_EXCHANGE, queueName, envelope, message -> {
            message.getMessageProperties().setHeader(RETRY_HEADER, nextRetryCount);
            return message;
        });
    }
}
