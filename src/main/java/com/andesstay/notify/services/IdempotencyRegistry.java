package com.andesstay.notify.services;

import com.andesstay.notify.persistence.ProcessedEvent;
import com.andesstay.notify.persistence.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Registro de idempotencia por {@code eventId}, persistido en PostgreSQL.
 * <p>
 * Garantiza que cada mensaje RabbitMQ se procese exactamente una vez, incluso
 * si el broker lo reentrega tras un reinicio del consumidor o un NACK con
 * {@code requeue=true}.
 * <p>
 * <strong>Implementación:</strong> tabla {@code processed_events} en PostgreSQL
 * (entidad {@link ProcessedEvent}). La PK es el {@code eventId}, por lo que
 * insertar un duplicado lanzaría una {@code DataIntegrityViolationException},
 * pero se usa {@link #alreadyProcessed} como guardia previa para evitarlo.
 * <p>
 * Flujo de uso en un listener:
 * <pre>
 *   if (idempotencyRegistry.alreadyProcessed(envelope.eventId())) {
 *       channel.basicAck(tag, false);  // ACK silencioso, ya procesado
 *       return;
 *   }
 *   // ... procesar ...
 *   idempotencyRegistry.markAsProcessed(envelope.eventId());
 * </pre>
 */
@Service
public class IdempotencyRegistry {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyRegistry.class);

    private final ProcessedEventRepository repository;

    public IdempotencyRegistry(ProcessedEventRepository repository) {
        this.repository = repository;
    }

    /**
     * Verifica si el evento ya fue procesado consultando la tabla {@code processed_events}.
     *
     * @param eventId ID único del evento.
     * @return {@code true} si ya se procesó (duplicado); {@code false} si es nuevo.
     */
    @Transactional(readOnly = true)
    public boolean alreadyProcessed(UUID eventId) {
        if (eventId == null) {
            log.warn("[IDEMPOTENCY] eventId nulo recibido; se tratará como no duplicado.");
            return false;
        }
        boolean exists = repository.existsById(eventId);
        if (exists) {
            log.debug("[IDEMPOTENCY] eventId {} ya procesado — descartando duplicado.", eventId);
        }
        return exists;
    }

    /**
     * Persiste el eventId como procesado en la tabla {@code processed_events}.
     *
     * @param eventId ID único del evento a registrar.
     */
    @Transactional
    public void markAsProcessed(UUID eventId) {
        if (eventId != null) {
            repository.save(new ProcessedEvent(eventId));
            log.debug("[IDEMPOTENCY] eventId {} registrado como procesado.", eventId);
        }
    }
}
