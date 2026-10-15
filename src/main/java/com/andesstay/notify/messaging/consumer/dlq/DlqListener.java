package com.andesstay.notify.messaging.consumer.dlq;

import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Listener de Dead Letter Queues (DLQ).
 * <p>
 * Consume las 3 DLQ del sistema y registra en nivel ERROR toda la información
 * disponible sobre cada mensaje fallido, incluyendo:
 * <ul>
 *   <li>Nombre de la DLQ de origen.</li>
 *   <li>Cuerpo crudo del mensaje (JSON).</li>
 *   <li>Causa del fallo (extraída de la cabecera {@code x-death}).</li>
 *   <li>Nombre de la cola origen (antes de ser enviado a DLQ).</li>
 *   <li>eventId si puede extraerse del cuerpo.</li>
 * </ul>
 * <p>
 * <strong>Política de DLQ:</strong> tras loguear, se hace basicAck para
 * eliminar el mensaje de la DLQ y evitar acumulación infinita.
 * En producción se podría persistir en base de datos para revisión manual.
 */
@Component
public class DlqListener {

    private static final Logger log = LoggerFactory.getLogger(DlqListener.class);

    /**
     * Consume mensajes de las 3 DLQ simultáneamente.
     * Los nombres de las colas se resuelven desde el bean rabbitProperties en el contexto.
     *
     * @param message  Mensaje AMQP crudo (incluye headers y body).
     * @param channel  Canal AMQP para ACK.
     * @param tag      Delivery tag.
     * @param queue    Nombre de la DLQ desde la que se recibió el mensaje.
     */
    @RabbitListener(queues = {
            "${app.rabbit.email.dlq}",
            "${app.rabbit.housekeeping.dlq}",
            "${app.rabbit.voucher.dlq}"
    })
    public void onDeadLetterMessage(
            Message message,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long tag,
            @Header(AmqpHeaders.CONSUMER_QUEUE) String queue)
            throws IOException {

        // ── Extraer cuerpo del mensaje como String ────────────────────────────
        String body = "(vacío)";
        if (message.getBody() != null && message.getBody().length > 0) {
            body = new String(message.getBody(), StandardCharsets.UTF_8);
        }

        // ── Extraer información de la cabecera x-death ────────────────────────
        // x-death es una lista de mapas que RabbitMQ agrega cuando mueve
        // un mensaje al DLX; contiene la causa y la cola de origen.
        String causa = "desconocida";
        String colaOrigen = "desconocida";
        String routingKey = "desconocida";
        long deathCount = 0;

        Object xDeath = message.getMessageProperties().getHeaders().get("x-death");
        if (xDeath instanceof List<?> deathList && !deathList.isEmpty()) {
            Object first = deathList.get(0);
            if (first instanceof Map<?, ?> rawMap) {
                @SuppressWarnings("unchecked")
                Map<Object, Object> deathMap = (Map<Object, Object>) rawMap;
                causa = String.valueOf(deathMap.getOrDefault("reason", "desconocida"));
                colaOrigen = String.valueOf(deathMap.getOrDefault("queue", "desconocida"));
                routingKey = String.valueOf(deathMap.getOrDefault("routing-keys", "desconocida"));
                Object count = deathMap.get("count");
                if (count instanceof Long c) deathCount = c;
            }
        }

        // ── Intentar extraer eventId del cuerpo JSON (búsqueda simple) ────────
        // No se deserializa con Jackson para evitar fallar si el JSON es inválido
        String eventId = extractEventId(body);

        // ── Log de nivel ERROR con toda la información disponible ─────────────
        log.error("""
                ╔══════════════════════════════════════════════════════════════╗
                ║  [DLQ] Mensaje no procesable — revisión manual requerida     ║
                ╠══════════════════════════════════════════════════════════════╣
                ║  DLQ destino   : {}
                ║  Cola origen   : {}
                ║  Routing key   : {}
                ║  Causa DLQ     : {}
                ║  Veces enviado : {}
                ║  eventId       : {}
                ╠══════════════════════════════════════════════════════════════╣
                ║  Cuerpo del mensaje:
                {}
                ╚══════════════════════════════════════════════════════════════╝
                """,
                queue, colaOrigen, routingKey, causa, deathCount, eventId, body);

        // ── ACK en la DLQ para evitar acumulación infinita ────────────────────
        // El mensaje ya está logueado; se confirma para liberar la DLQ.
        channel.basicAck(tag, false);
    }

    /**
     * Extrae el valor de {@code eventId} del JSON sin deserialización completa.
     * Útil cuando el JSON puede estar malformado.
     *
     * @param json Cuerpo del mensaje en formato String.
     * @return El valor del eventId si se encuentra; "(no extraíble)" en caso contrario.
     */
    private String extractEventId(String json) {
        if (json == null || json.isBlank()) return "(no extraíble)";
        try {
            int idx = json.indexOf("\"eventId\"");
            if (idx == -1) return "(no extraíble)";
            int colon = json.indexOf(':', idx);
            if (colon == -1) return "(no extraíble)";
            int start = json.indexOf('"', colon + 1);
            if (start == -1) return "(no extraíble)";
            int end = json.indexOf('"', start + 1);
            if (end == -1) return "(no extraíble)";
            return json.substring(start + 1, end);
        } catch (Exception e) {
            return "(error al extraer)";
        }
    }
}
