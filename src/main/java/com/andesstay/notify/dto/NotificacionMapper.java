package com.andesstay.notify.dto;

import com.andesstay.notify.entities.Notificacion;

/**
 * Mapper estático para convertir entre {@link Notificacion} y {@link NotificacionDTO}.
 * <p>
 * Se usa un mapper estático simple en lugar de MapStruct para minimizar
 * dependencias. Si el proyecto crece, considerar migrar a MapStruct.
 */
public final class NotificacionMapper {

    private NotificacionMapper() {
        // Utility class — no instanciable
    }

    /**
     * Convierte una entidad {@link Notificacion} a su DTO de respuesta.
     *
     * @param entity Entidad JPA.
     * @return DTO con los campos expuestos a la API.
     */
    public static NotificacionDTO toDTO(Notificacion entity) {
        if (entity == null) return null;
        return new NotificacionDTO(
                entity.getId(),
                entity.getCanal() != null ? entity.getCanal().name() : null,
                entity.getDestinatario(),
                entity.getAsunto(),
                entity.getContenido(),
                entity.getEstado() != null ? entity.getEstado().name() : null,
                entity.getEventId(),
                entity.getReservationId(),
                entity.getCreatedAt(),
                entity.getErrorMessage()
        );
    }
}
