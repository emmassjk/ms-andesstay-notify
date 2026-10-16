package com.andesstay.notify.services;

import com.andesstay.notify.dto.NotificacionDTO;
import com.andesstay.notify.dto.NotificacionMapper;
import com.andesstay.notify.entities.Notificacion;
import com.andesstay.notify.entities.Notificacion.CanalNotificacion;
import com.andesstay.notify.entities.Notificacion.EstadoNotificacion;
import com.andesstay.notify.event.ReservationEnvelope;
import com.andesstay.notify.repository.NotificacionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Servicio de dominio para la gestión de notificaciones.
 * <p>
 * Centraliza la lógica de negocio de creación, consulta y actualización
 * de estado de las notificaciones. Es consumido tanto por los listeners
 * de RabbitMQ como por el controlador REST.
 */
@Service
public class NotificacionService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);

    private final NotificacionRepository notificacionRepository;

    public NotificacionService(NotificacionRepository notificacionRepository) {
        this.notificacionRepository = notificacionRepository;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CONSULTAS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Obtiene todas las notificaciones. */
    @Transactional(readOnly = true)
    public List<NotificacionDTO> findAll() {
        return notificacionRepository.findAll()
                .stream()
                .map(NotificacionMapper::toDTO)
                .toList();
    }

    /** Obtiene una notificación por su ID. */
    @Transactional(readOnly = true)
    public Optional<NotificacionDTO> findById(Long id) {
        return notificacionRepository.findById(id)
                .map(NotificacionMapper::toDTO);
    }

    /** Obtiene notificaciones filtradas por canal. */
    @Transactional(readOnly = true)
    public List<NotificacionDTO> findByCanal(CanalNotificacion canal) {
        return notificacionRepository.findByCanal(canal)
                .stream()
                .map(NotificacionMapper::toDTO)
                .toList();
    }

    /** Obtiene notificaciones filtradas por estado. */
    @Transactional(readOnly = true)
    public List<NotificacionDTO> findByEstado(EstadoNotificacion estado) {
        return notificacionRepository.findByEstado(estado)
                .stream()
                .map(NotificacionMapper::toDTO)
                .toList();
    }

    /** Obtiene notificaciones asociadas a una reserva. */
    @Transactional(readOnly = true)
    public List<NotificacionDTO> findByReservationId(Long reservationId) {
        return notificacionRepository.findByReservationId(reservationId)
                .stream()
                .map(NotificacionMapper::toDTO)
                .toList();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // OPERACIONES DE ESCRITURA (usadas por los listeners)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Registra una notificación exitosa después de que un listener procesó
     * un mensaje de RabbitMQ correctamente.
     *
     * @param canal    Canal de notificación.
     * @param envelope Envelope del evento procesado.
     * @param asunto   Asunto descriptivo de la notificación.
     * @param contenido Contenido o detalle de lo que se notificó.
     * @return La notificación persistida como DTO.
     */
    @Transactional
    public NotificacionDTO registrarNotificacionExitosa(
            CanalNotificacion canal,
            ReservationEnvelope envelope,
            String asunto,
            String contenido) {

        var payload = envelope.payload();
        String destinatario = switch (canal) {
            case EMAIL -> payload.guestEmail();
            case HOUSEKEEPING -> payload.operatorEmail();
            case VOUCHER -> payload.guestEmail();
        };

        Notificacion notificacion = new Notificacion(
                canal, destinatario, asunto, contenido,
                envelope.eventId(), payload.reservationId()
        );
        notificacion.setEstado(EstadoNotificacion.ENVIADA);

        Notificacion saved = notificacionRepository.save(notificacion);
        log.info("[NOTIFY-SVC] Notificación registrada: canal={}, eventId={}, id={}",
                canal, envelope.eventId(), saved.getId());
        return NotificacionMapper.toDTO(saved);
    }

    /**
     * Registra una notificación fallida cuando un listener encuentra un error
     * no recuperable.
     *
     * @param canal        Canal de notificación.
     * @param envelope     Envelope del evento fallido.
     * @param errorMessage Descripción del error.
     * @return La notificación persistida como DTO.
     */
    @Transactional
    public NotificacionDTO registrarNotificacionFallida(
            CanalNotificacion canal,
            ReservationEnvelope envelope,
            String errorMessage) {

        String destinatario = "desconocido";
        Long reservationId = null;

        if (envelope != null && envelope.payload() != null) {
            var payload = envelope.payload();
            destinatario = payload.guestEmail() != null ? payload.guestEmail() : "desconocido";
            reservationId = payload.reservationId();
        }

        Notificacion notificacion = new Notificacion(
                canal, destinatario, "Error en notificación",
                errorMessage, envelope != null ? envelope.eventId() : null, reservationId
        );
        notificacion.setEstado(EstadoNotificacion.FALLIDA);
        notificacion.setErrorMessage(errorMessage);

        Notificacion saved = notificacionRepository.save(notificacion);
        log.warn("[NOTIFY-SVC] Notificación fallida registrada: canal={}, id={}, error={}",
                canal, saved.getId(), errorMessage);
        return NotificacionMapper.toDTO(saved);
    }
}
