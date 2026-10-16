package com.andesstay.notify.controller;

import com.andesstay.notify.dto.NotificacionDTO;
import com.andesstay.notify.entities.Notificacion.CanalNotificacion;
import com.andesstay.notify.entities.Notificacion.EstadoNotificacion;
import com.andesstay.notify.model.NotifyConstants;
import com.andesstay.notify.services.NotificacionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controlador REST para consultar el historial de notificaciones.
 * <p>
 * Todos los endpoints requieren un JWT válido en producción
 * (configurado en {@link com.andesstay.notify.config.SecurityConfig}).
 * <p>
 * Base path: {@code /api/v1/notificaciones}
 * <p>
 * Endpoints:
 * <ul>
 *   <li>{@code GET /}                         — Todas las notificaciones.</li>
 *   <li>{@code GET /{id}}                     — Notificación por ID.</li>
 *   <li>{@code GET /canal/{canal}}            — Filtrar por canal.</li>
 *   <li>{@code GET /estado/{estado}}          — Filtrar por estado.</li>
 *   <li>{@code GET /reserva/{reservationId}}  — Filtrar por reserva.</li>
 * </ul>
 */
@RestController
@RequestMapping(NotifyConstants.API_BASE)
public class NotificacionController {

    private static final Logger log = LoggerFactory.getLogger(NotificacionController.class);

    private final NotificacionService notificacionService;

    public NotificacionController(NotificacionService notificacionService) {
        this.notificacionService = notificacionService;
    }

    /**
     * Obtiene todas las notificaciones registradas.
     *
     * @return Lista de {@link NotificacionDTO}.
     */
    @GetMapping
    public ResponseEntity<List<NotificacionDTO>> findAll() {
        log.debug("GET {} — solicitando todas las notificaciones", NotifyConstants.API_BASE);
        List<NotificacionDTO> result = notificacionService.findAll();
        return ResponseEntity.ok(result);
    }

    /**
     * Obtiene una notificación por su ID.
     *
     * @param id ID de la notificación.
     * @return La notificación si existe; 404 en caso contrario.
     */
    @GetMapping("/{id}")
    public ResponseEntity<NotificacionDTO> findById(@PathVariable Long id) {
        log.debug("GET {}/{} — buscando notificación por id", NotifyConstants.API_BASE, id);
        return notificacionService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Filtra notificaciones por canal (EMAIL, HOUSEKEEPING, VOUCHER).
     *
     * @param canal Nombre del canal de notificación.
     * @return Lista filtrada o 400 si el canal es inválido.
     */
    @GetMapping(NotifyConstants.API_BY_CANAL)
    public ResponseEntity<List<NotificacionDTO>> findByCanal(@PathVariable String canal) {
        log.debug("GET {}/canal/{} — filtrando por canal", NotifyConstants.API_BASE, canal);
        try {
            CanalNotificacion canalEnum = CanalNotificacion.valueOf(canal.toUpperCase());
            return ResponseEntity.ok(notificacionService.findByCanal(canalEnum));
        } catch (IllegalArgumentException e) {
            log.warn("Canal inválido recibido: {}", canal);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Filtra notificaciones por estado (PENDIENTE, ENVIADA, FALLIDA).
     *
     * @param estado Nombre del estado de la notificación.
     * @return Lista filtrada o 400 si el estado es inválido.
     */
    @GetMapping(NotifyConstants.API_BY_ESTADO)
    public ResponseEntity<List<NotificacionDTO>> findByEstado(@PathVariable String estado) {
        log.debug("GET {}/estado/{} — filtrando por estado", NotifyConstants.API_BASE, estado);
        try {
            EstadoNotificacion estadoEnum = EstadoNotificacion.valueOf(estado.toUpperCase());
            return ResponseEntity.ok(notificacionService.findByEstado(estadoEnum));
        } catch (IllegalArgumentException e) {
            log.warn("Estado inválido recibido: {}", estado);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Obtiene todas las notificaciones asociadas a una reserva.
     *
     * @param reservationId ID de la reserva.
     * @return Lista de notificaciones de esa reserva.
     */
    @GetMapping(NotifyConstants.API_BY_RESERVA)
    public ResponseEntity<List<NotificacionDTO>> findByReservationId(@PathVariable Long reservationId) {
        log.debug("GET {}/reserva/{} — filtrando por reserva", NotifyConstants.API_BASE, reservationId);
        return ResponseEntity.ok(notificacionService.findByReservationId(reservationId));
    }
}
