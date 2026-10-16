package com.andesstay.notify.repository;

import com.andesstay.notify.entities.Notificacion;
import com.andesstay.notify.entities.Notificacion.CanalNotificacion;
import com.andesstay.notify.entities.Notificacion.EstadoNotificacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio JPA para {@link Notificacion}.
 * <p>
 * Spring Data genera la implementación automáticamente.
 * Los métodos derivados permiten consultar notificaciones por canal,
 * estado, reserva o evento.
 */
public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    /** Busca todas las notificaciones de un canal específico (EMAIL, HOUSEKEEPING, VOUCHER). */
    List<Notificacion> findByCanal(CanalNotificacion canal);

    /** Busca todas las notificaciones con un estado específico (PENDIENTE, ENVIADA, FALLIDA). */
    List<Notificacion> findByEstado(EstadoNotificacion estado);

    /** Busca todas las notificaciones asociadas a una reserva. */
    List<Notificacion> findByReservationId(Long reservationId);

    /** Busca una notificación por su eventId (para verificar si ya fue registrada). */
    Optional<Notificacion> findByEventId(UUID eventId);

    /** Busca notificaciones por canal y estado simultáneamente. */
    List<Notificacion> findByCanalAndEstado(CanalNotificacion canal, EstadoNotificacion estado);
}
