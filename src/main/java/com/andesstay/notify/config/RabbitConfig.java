package com.andesstay.notify.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Configuración de la topología completa de RabbitMQ.
 * <p>
 * <strong>Responsabilidad única:</strong> esta es la ÚNICA clase que conoce la
 * estructura de exchanges, colas, DLQ y bindings. Todos los nombres provienen de
 * {@link RabbitProperties} (que los lee de application.yaml); no hay ningún
 * String literal de mensajería en este archivo.
 * <p>
 * Topología declarada:
 * <pre>
 * Exchange direct : cmd.direct
 * Exchange topic  : cmd.topic
 * Exchange DLX    : cmd.dead.dlx  (para Dead Letter Queues)
 *
 * q.cmd.email         ←  direct: email.send       | topic: email.*
 *   └─ DLQ: q.cmd.email.dlq
 *
 * q.cmd.housekeeping  ←  direct: housekeeping.ticket | topic: housekeeping.#
 *   └─ DLQ: q.cmd.housekeeping.dlq
 *
 * q.cmd.voucher       ←  direct: voucher.gen      | topic: voucher.*
 *   └─ DLQ: q.cmd.voucher.dlq
 * </pre>
 */
@Configuration
public class RabbitConfig {

    /** Propiedades de topología leídas de application.yaml. */
    private final RabbitProperties props;

    public RabbitConfig(RabbitProperties props) {
        this.props = props;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // EXCHANGES
    // ═══════════════════════════════════════════════════════════════════════════

    /** Exchange principal de tipo DIRECT para routing exacto por routing-key. */
    @Bean
    public DirectExchange directExchange() {
        return ExchangeBuilder
                .directExchange(props.exchangeDirect())
                .durable(true)
                .build();
    }

    /** Exchange principal de tipo TOPIC para routing con patrones (*, #). */
    @Bean
    public TopicExchange topicExchange() {
        return ExchangeBuilder
                .topicExchange(props.exchangeTopic())
                .durable(true)
                .build();
    }

    /**
     * Exchange DLX (Dead Letter Exchange) de tipo DIRECT.
     * Las colas principales apuntan a este exchange cuando un mensaje es rechazado
     * con requeue=false (NACK definitivo tras error no recuperable o agotamiento
     * de reintentos).
     */
    @Bean
    public DirectExchange deadLetterExchange() {
        return ExchangeBuilder
                .directExchange(props.exchangeDlx())
                .durable(true)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // COLAS — EMAIL
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Cola principal de email.
     * Se declara con:
     * - {@code x-dead-letter-exchange}    → cmd.dead.dlx
     * - {@code x-dead-letter-routing-key} → routing key de la DLQ de email
     * Esto garantiza que cualquier NACK con requeue=false sea redirigido a la DLQ.
     */
    @Bean
    public Queue emailQueue() {
        return QueueBuilder
                .durable(props.email().queue())
                .withArgument("x-dead-letter-exchange", props.exchangeDlx())
                .withArgument("x-dead-letter-routing-key", props.email().routingKeyDlq())
                .build();
    }

    /** Dead Letter Queue para mensajes de email rechazados definitivamente. */
    @Bean
    public Queue emailDlq() {
        return QueueBuilder.durable(props.email().dlq()).build();
    }

    /** Binding: exchange direct → cola email (routing-key exacta). */
    @Bean
    public Binding emailDirectBinding(DirectExchange directExchange, Queue emailQueue) {
        return BindingBuilder
                .bind(emailQueue)
                .to(directExchange)
                .with(props.email().routingKeyDirect());
    }

    /** Binding: exchange topic → cola email (patrón de routing-key). */
    @Bean
    public Binding emailTopicBinding(TopicExchange topicExchange, Queue emailQueue) {
        return BindingBuilder
                .bind(emailQueue)
                .to(topicExchange)
                .with(props.email().routingKeyTopic());
    }

    /** Binding: exchange DLX → DLQ de email. */
    @Bean
    public Binding emailDlqBinding(DirectExchange deadLetterExchange, Queue emailDlq) {
        return BindingBuilder
                .bind(emailDlq)
                .to(deadLetterExchange)
                .with(props.email().routingKeyDlq());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // COLAS — HOUSEKEEPING
    // ═══════════════════════════════════════════════════════════════════════════

    /** Cola principal de housekeeping con DLX configurado. */
    @Bean
    public Queue housekeepingQueue() {
        return QueueBuilder
                .durable(props.housekeeping().queue())
                .withArgument("x-dead-letter-exchange", props.exchangeDlx())
                .withArgument("x-dead-letter-routing-key", props.housekeeping().routingKeyDlq())
                .build();
    }

    /** Dead Letter Queue para tickets de housekeeping rechazados definitivamente. */
    @Bean
    public Queue housekeepingDlq() {
        return QueueBuilder.durable(props.housekeeping().dlq()).build();
    }

    /** Binding: exchange direct → cola housekeeping. */
    @Bean
    public Binding housekeepingDirectBinding(DirectExchange directExchange, Queue housekeepingQueue) {
        return BindingBuilder
                .bind(housekeepingQueue)
                .to(directExchange)
                .with(props.housekeeping().routingKeyDirect());
    }

    /** Binding: exchange topic → cola housekeeping (patrón housekeeping.#). */
    @Bean
    public Binding housekeepingTopicBinding(TopicExchange topicExchange, Queue housekeepingQueue) {
        return BindingBuilder
                .bind(housekeepingQueue)
                .to(topicExchange)
                .with(props.housekeeping().routingKeyTopic());
    }

    /** Binding: exchange DLX → DLQ de housekeeping. */
    @Bean
    public Binding housekeepingDlqBinding(DirectExchange deadLetterExchange, Queue housekeepingDlq) {
        return BindingBuilder
                .bind(housekeepingDlq)
                .to(deadLetterExchange)
                .with(props.housekeeping().routingKeyDlq());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // COLAS — VOUCHER
    // ═══════════════════════════════════════════════════════════════════════════

    /** Cola principal de voucher con DLX configurado. */
    @Bean
    public Queue voucherQueue() {
        return QueueBuilder
                .durable(props.voucher().queue())
                .withArgument("x-dead-letter-exchange", props.exchangeDlx())
                .withArgument("x-dead-letter-routing-key", props.voucher().routingKeyDlq())
                .build();
    }

    /** Dead Letter Queue para vouchers rechazados definitivamente. */
    @Bean
    public Queue voucherDlq() {
        return QueueBuilder.durable(props.voucher().dlq()).build();
    }

    /** Binding: exchange direct → cola voucher. */
    @Bean
    public Binding voucherDirectBinding(DirectExchange directExchange, Queue voucherQueue) {
        return BindingBuilder
                .bind(voucherQueue)
                .to(directExchange)
                .with(props.voucher().routingKeyDirect());
    }

    /** Binding: exchange topic → cola voucher. */
    @Bean
    public Binding voucherTopicBinding(TopicExchange topicExchange, Queue voucherQueue) {
        return BindingBuilder
                .bind(voucherQueue)
                .to(topicExchange)
                .with(props.voucher().routingKeyTopic());
    }

    /** Binding: exchange DLX → DLQ de voucher. */
    @Bean
    public Binding voucherDlqBinding(DirectExchange deadLetterExchange, Queue voucherDlq) {
        return BindingBuilder
                .bind(voucherDlq)
                .to(deadLetterExchange)
                .with(props.voucher().routingKeyDlq());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // INFRAESTRUCTURA DE MENSAJERÍA
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Convertidor JSON ↔ AMQP Message.
     * Usa Jackson para serializar/deserializar el envelope {@link com.andesstay.notify.event.ReservationEnvelope}.
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * RabbitTemplate configurado con el convertidor JSON.
     * Aunque este microservicio es principalmente consumidor, el template es útil
     * para publicaciones de respuesta o diagnóstico.
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    /**
     * Factory de listeners configurada con ACK manual y el convertidor JSON.
     * <p>
     * ACK manual significa que cada listener debe llamar explícitamente a:
     * <ul>
     *   <li>{@code channel.basicAck}  – procesamiento exitoso</li>
     *   <li>{@code channel.basicNack(requeue=true)}  – error recuperable (reintento)</li>
     *   <li>{@code channel.basicNack(requeue=false)} – error no recuperable → DLQ</li>
     * </ul>
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        // ACK manual: el listener controla explícitamente el acknowledge
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        // Red de seguridad: una excepción no controlada NO reencola (evita bucles infinitos);
        // el mensaje rechazado va al DLX → DLQ.
        factory.setDefaultRequeueRejected(false);
        factory.setPrefetchCount(10);
        factory.setConcurrentConsumers(2);
        factory.setMaxConcurrentConsumers(5);
        return factory;
    }
}
