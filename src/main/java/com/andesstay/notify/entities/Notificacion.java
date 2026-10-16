package com.andesstay.notify.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Entidad JPA que representa una notificación persistida en la base de datos.
 * <p>
 * Cada vez que el microservicio procesa un mensaje de RabbitMQ (email,
 * housekeeping o voucher), se crea un registro en esta tabla para auditoría
 * y trazabilidad.
 * <p>
 * Tabla: {@code notificaciones}
 */
@Entity
@Table(name = "notificaciones")
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Tipo de canal de notificación (EMAIL, HOUSEKEEPING, VOUCHER). */
    @Enumerated(EnumType.STRING)
    @Column(name = "canal", nullable = false, length = 30)
    private CanalNotificacion canal;

    /** Destinatario principal de la notificación (email del huésped u operador). */
    @Column(name = "destinatario", nullable = false)
    private String destinatario;

    /** Asunto o título descriptivo de la notificación. */
    @Column(name = "asunto", nullable = false, length = 500)
    private String asunto;

    /** Cuerpo o contenido de la notificación (puede ser HTML o texto plano). */
    @Column(name = "contenido", columnDefinition = "TEXT")
    private String contenido;

    /** Estado actual de la notificación. */
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoNotificacion estado;

    /** ID del evento de RabbitMQ que originó esta notificación (para trazabilidad). */
    @Column(name = "event_id")
    private UUID eventId;

    /** ID de la reserva asociada. */
    @Column(name = "reservation_id")
    private Long reservationId;

    /** Timestamp de creación del registro. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Timestamp de última actualización. */
    @Column(name = "updated_at")
    private Instant updatedAt;

    /** Mensaje de error si la notificación falló. */
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    // ─── Enums internos ──────────────────────────────────────────────────────

    public enum CanalNotificacion {
        EMAIL, HOUSEKEEPING, VOUCHER
    }

    public enum EstadoNotificacion {
        PENDIENTE, ENVIADA, FALLIDA
    }

    // ─── Lifecycle callbacks ─────────────────────────────────────────────────

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        if (this.estado == null) {
            this.estado = EstadoNotificacion.PENDIENTE;
        }
    }

    // ─── Constructores ───────────────────────────────────────────────────────

    /** Constructor sin argumentos requerido por JPA. */
    protected Notificacion() {}

    public Notificacion(CanalNotificacion canal, String destinatario, String asunto,
                        String contenido, UUID eventId, Long reservationId) {
        this.canal = canal;
        this.destinatario = destinatario;
        this.asunto = asunto;
        this.contenido = contenido;
        this.eventId = eventId;
        this.reservationId = reservationId;
        this.estado = EstadoNotificacion.PENDIENTE;
    }

    // ─── Getters & Setters ───────────────────────────────────────────────────

    public Long getId() { return id; }

    public CanalNotificacion getCanal() { return canal; }
    public void setCanal(CanalNotificacion canal) { this.canal = canal; }

    public String getDestinatario() { return destinatario; }
    public void setDestinatario(String destinatario) { this.destinatario = destinatario; }

    public String getAsunto() { return asunto; }
    public void setAsunto(String asunto) { this.asunto = asunto; }

    public String getContenido() { return contenido; }
    public void setContenido(String contenido) { this.contenido = contenido; }

    public EstadoNotificacion getEstado() { return estado; }
    public void setEstado(EstadoNotificacion estado) {
        this.estado = estado;
        this.updatedAt = Instant.now();
    }

    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }

    public Long getReservationId() { return reservationId; }
    public void setReservationId(Long reservationId) { this.reservationId = reservationId; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        this.updatedAt = Instant.now();
    }
}
