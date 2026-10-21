package com.andesstay.notify.messaging.producer;

import com.andesstay.notify.TestData;
import com.andesstay.notify.dto.EnviarNotificacionRequest;
import com.andesstay.notify.dto.EnviarNotificacionResponse;
import com.andesstay.notify.entities.Notificacion.CanalNotificacion;
import com.andesstay.notify.event.ReservationEnvelope;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotificacionProducerTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final NotificacionProducer producer =
            new NotificacionProducer(rabbitTemplate, TestData.rabbitProperties());

    private EnviarNotificacionRequest request(CanalNotificacion canal) {
        return new EnviarNotificacionRequest(canal, 7L, "Ana Soto", "ana@example.com",
                "Cabaña Lago", "C-3", LocalDate.now(), LocalDate.now().plusDays(2),
                new BigDecimal("90000"), "CLP", "operador@andesstay.cl", "cama extra");
    }

    @Test
    void publicaEnRoutingKeyDeCadaCanal() {
        assertRoute(CanalNotificacion.EMAIL, "email.send");
        assertRoute(CanalNotificacion.HOUSEKEEPING, "housekeeping.ticket");
        assertRoute(CanalNotificacion.VOUCHER, "voucher.gen");
    }

    private void assertRoute(CanalNotificacion canal, String routingKey) {
        EnviarNotificacionResponse res = producer.publicar(request(canal));
        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(eq("cmd.direct"), eq(routingKey), body.capture());
        ReservationEnvelope env = (ReservationEnvelope) body.getValue();
        assertEquals(res.eventId(), env.eventId());
        assertNotNull(env.payload());
        assertEquals("Ana Soto", env.payload().guestName());
        assertEquals(routingKey, res.routingKey());
        assertEquals("ENCOLADA", res.estado());
    }
}
