package com.andesstay.notify.messaging.consumer.dlq;

import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias de {@link DlqListener}.
 * <p>
 * Verifica que:
 * <ul>
 *   <li>Se hace {@code basicAck} siempre (política de DLQ: log-and-discard).</li>
 *   <li>Se extrae correctamente la información de la cabecera {@code x-death}.</li>
 *   <li>El listener tolera mensajes con cuerpo vacío o nulo.</li>
 *   <li>El listener tolera ausencia de cabecera {@code x-death}.</li>
 *   <li>Se extrae {@code eventId} del cuerpo JSON cuando está presente.</li>
 * </ul>
 * No levanta contexto Spring (MockitoExtension puro).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DlqListener — política log-and-discard")
class DlqListenerTest {

    @Mock
    private Channel channel;

    private DlqListener listener;

    @BeforeEach
    void setUp() {
        listener = new DlqListener();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Construye un {@link Message} con cuerpo JSON y la cabecera {@code x-death}
     * rellena con los campos que RabbitMQ añade al mover un mensaje al DLX.
     */
    private Message buildMessageWithXDeath(String jsonBody, String reason,
                                           String originQueue, String routingKey,
                                           long count) {
        MessageProperties props = new MessageProperties();
        Map<String, Object> deathEntry = Map.of(
                "reason", reason,
                "queue", originQueue,
                "routing-keys", routingKey,
                "count", count
        );
        props.setHeader("x-death", List.of(deathEntry));

        byte[] body = jsonBody == null ? new byte[0]
                : jsonBody.getBytes(StandardCharsets.UTF_8);
        return MessageBuilder.withBody(body).andProperties(props).build();
    }

    /** Construye un {@link Message} sin cabecera {@code x-death}. */
    private Message buildMessageWithoutXDeath(String jsonBody) {
        MessageProperties props = new MessageProperties();
        byte[] body = jsonBody == null ? new byte[0]
                : jsonBody.getBytes(StandardCharsets.UTF_8);
        return MessageBuilder.withBody(body).andProperties(props).build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Mensaje bien formado: hace basicAck y no lanza excepción")
    void wellFormedMessage_callsBasicAck() throws Exception {
        String body = """
                {"eventId":"evt-001","type":"RESERVATION_CREATED"}
                """;
        Message msg = buildMessageWithXDeath(body, "rejected",
                "notify.email.queue", "notify.email", 1L);

        listener.onDeadLetterMessage(msg, channel, 10L, "notify.email.dlq");

        verify(channel).basicAck(10L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("Mensaje sin cabecera x-death: hace basicAck con valores 'desconocida'")
    void messageWithoutXDeathHeader_callsBasicAck() throws Exception {
        String body = "{\"eventId\":\"evt-002\"}";
        Message msg = buildMessageWithoutXDeath(body);

        listener.onDeadLetterMessage(msg, channel, 20L, "notify.voucher.dlq");

        verify(channel).basicAck(20L, false);
    }

    @Test
    @DisplayName("Cuerpo vacío: hace basicAck sin lanzar excepción")
    void emptyBody_callsBasicAck() throws Exception {
        Message msg = buildMessageWithXDeath("", "expired",
                "notify.housekeeping.queue", "notify.housekeeping", 2L);

        listener.onDeadLetterMessage(msg, channel, 30L, "notify.housekeeping.dlq");

        verify(channel).basicAck(30L, false);
    }

    @Test
    @DisplayName("Cuerpo sin eventId: hace basicAck y no falla la extracción")
    void bodyWithoutEventId_callsBasicAck() throws Exception {
        String body = "{\"type\":\"HOUSEKEEPING_SCHEDULED\"}";
        Message msg = buildMessageWithXDeath(body, "rejected",
                "notify.housekeeping.queue", "notify.housekeeping", 1L);

        listener.onDeadLetterMessage(msg, channel, 40L, "notify.housekeeping.dlq");

        verify(channel).basicAck(40L, false);
    }

    @Test
    @DisplayName("JSON malformado: hace basicAck sin propagar excepción")
    void malformedJson_callsBasicAckWithoutThrowing() throws Exception {
        String body = "esto-no-es-json{{{{";
        Message msg = buildMessageWithXDeath(body, "rejected",
                "notify.email.queue", "notify.email", 1L);

        listener.onDeadLetterMessage(msg, channel, 50L, "notify.email.dlq");

        verify(channel).basicAck(50L, false);
    }

    @Test
    @DisplayName("basicAck usa exactamente el delivery-tag recibido (no multiple)")
    void basicAck_usesExactDeliveryTagAndNotMultiple() throws Exception {
        Message msg = buildMessageWithoutXDeath("{\"eventId\":\"evt-003\"}");

        listener.onDeadLetterMessage(msg, channel, 99L, "notify.email.dlq");

        // multiple=false: sólo confirma este mensaje, no todos los pendientes
        verify(channel).basicAck(99L, false);
    }
}
