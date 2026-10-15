package com.andesstay.notify.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de topología RabbitMQ leídas exclusivamente desde application.yaml
 * bajo el prefijo {@code app.rabbit}.
 * <p>
 * <strong>Regla de diseño:</strong> ninguna clase fuera de este record y de
 * {@link RabbitConfig} debe conocer nombres de exchanges, colas o routing-keys.
 * Todos los strings de mensajería viven en application.yaml y se acceden a través
 * de este record.
 *
 * @param exchangeDirect Nombre del exchange direct principal (cmd.direct).
 * @param exchangeTopic  Nombre del exchange topic principal (cmd.topic).
 * @param exchangeDlx    Nombre del exchange DLX/DLQ (cmd.dead.dlx).
 * @param email          Configuración del canal de email.
 * @param housekeeping   Configuración del canal de housekeeping.
 * @param voucher        Configuración del canal de voucher.
 * @param maxRetryAttempts Número máximo de reintentos para errores recuperables.
 */
@ConfigurationProperties(prefix = "app.rabbit")
public record RabbitProperties(
        String exchangeDirect,
        String exchangeTopic,
        String exchangeDlx,
        ChannelProps email,
        ChannelProps housekeeping,
        ChannelProps voucher,
        int maxRetryAttempts
) {

    /**
     * Propiedades de un canal de mensajería (cola principal + DLQ + routing-keys).
     *
     * @param queue              Nombre de la cola principal.
     * @param dlq                Nombre de la Dead Letter Queue.
     * @param routingKeyDirect   Routing key para el exchange direct.
     * @param routingKeyTopic    Routing key (patrón) para el exchange topic.
     * @param routingKeyDlq      Routing key para enrutar hacia la DLQ en el exchange DLX.
     */
    public record ChannelProps(
            String queue,
            String dlq,
            String routingKeyDirect,
            String routingKeyTopic,
            String routingKeyDlq
    ) {}
}
