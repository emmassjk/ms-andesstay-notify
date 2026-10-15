package com.andesstay.notify.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Entidad JPA que persiste los eventIds ya procesados en PostgreSQL.
 * <p>
 * Tabla: {@code processed_events}
 * <ul>
 *   <li>{@code event_id}    — PK, el UUID del evento.</li>
 *   <li>{@code processed_at} — timestamp de cuándo se procesó.</li>
 * </ul>
 * Hibernate crea la tabla automáticamente con {@code ddl-auto: update} (profile dev).
 */
@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", updatable = false, nullable = false)
    private UUID eventId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    /** Constructor sin argumentos requerido por JPA. */
    protected ProcessedEvent() {}

    public ProcessedEvent(UUID eventId) {
        this.eventId = eventId;
        this.processedAt = Instant.now();
    }

    public UUID getEventId() { return eventId; }
    public Instant getProcessedAt() { return processedAt; }
}
