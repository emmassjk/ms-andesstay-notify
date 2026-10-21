package com.andesstay.notify.messaging.producer;

import com.andesstay.notify.config.RabbitProperties;
import com.andesstay.notify.dto.EnviarNotificacionRequest;
import com.andesstay.notify.dto.EnviarNotificacionResponse;
import com.andesstay.notify.event.ReservationEnvelope;
import com.andesstay.notify.event.ReservationPayload;
import com.andesstay.notify.model.TipoEvento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * PRODUCER de ms-andesstay-notify.
 * <p>
 * Recibe la solicitud ya validada, arma el {@link ReservationEnvelope} (el mismo
 * formato que consumen los listeners) y lo publica en el exchange {@code cmd.direct}
 * con la routing key del canal elegido:
 * <ul>
 *   <li>EMAIL        → {@code email.send}        → {@code q.cmd.email}</li>
 *   <li>HOUSEKEEPING → {@code housekeeping.ticket} → {@code q.cmd.housekeeping}</li>
 *   <li>VOUCHER      → {@code voucher.gen}       → {@code q.cmd.voucher}</li>
 * </ul>
 * Los nombres salen de {@link RabbitProperties} (application.yaml); aquí no hay
 * strings de mensajería.
 */
@Component
public class NotificacionProducer {

    private static final Logger log = LoggerFactory.getLogger(NotificacionProducer.class);

    private final RabbitTemplate rabbitTemplate;
    private final RabbitProperties props;

    public NotificacionProducer(RabbitTemplate rabbitTemplate, RabbitProperties props) {
        this.rabbitTemplate = rabbitTemplate;
        this.props = props;
    }

    /**
     * Publica la solicitud en RabbitMQ.
     *
     * @throws org.springframework.amqp.AmqpException si el broker no está disponible.
     */
    public EnviarNotificacionResponse publicar(EnviarNotificacionRequest req) {
        String routingKey = routingKeyPara(req);
        UUID eventId = UUID.randomUUID();
        Instant ahora = Instant.now();

        ReservationPayload payload = new ReservationPayload(
                req.reservationId(), req.guestName(), req.guestEmail(),
                req.propertyName(), req.roomNumber(), req.checkIn(), req.checkOut(),
                req.totalAmount(), req.currency(), req.operatorEmail(), req.specialRequests());

        ReservationEnvelope envelope = new ReservationEnvelope(
                TipoEvento.RESERVATION_CREATED.name(), eventId, ahora,
                eventId.toString(), eventId.toString(), payload);

        rabbitTemplate.convertAndSend(props.exchangeDirect(), routingKey, envelope);
        log.info("[PRODUCER] Evento publicado. eventId={}, canal={}, exchange={}, routingKey={}",
                eventId, req.canal(), props.exchangeDirect(), routingKey);

        return new EnviarNotificacionResponse(eventId, req.canal().name(), routingKey, "ENCOLADA", ahora);
    }

    private String routingKeyPara(EnviarNotificacionRequest req) {
        return switch (req.canal()) {
            case EMAIL -> props.email().routingKeyDirect();
            case HOUSEKEEPING -> props.housekeeping().routingKeyDirect();
            case VOUCHER -> props.voucher().routingKeyDirect();
        };
    }
}
