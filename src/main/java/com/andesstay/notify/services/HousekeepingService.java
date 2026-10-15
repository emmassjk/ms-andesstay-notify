package com.andesstay.notify.services;

import com.andesstay.notify.event.ReservationEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Servicio simulado de generación de tickets de housekeeping.
 * <p>
 * En producción, crearía un ticket en el sistema de gestión de tareas del hostal
 * (p.ej. limpieza/preparación de habitación antes del check-in del huésped).
 * <p>
 * Por ahora registra en logs el ticket que «crearía».
 */
@Service
public class HousekeepingService {

    private static final Logger log = LoggerFactory.getLogger(HousekeepingService.class);

    /**
     * Simula la creación de un ticket de housekeeping para preparar
     * la habitación antes del check-in del huésped.
     *
     * @param envelope Envelope del evento de reserva.
     */
    public void createHousekeepingTicket(ReservationEnvelope envelope) {
        var payload = envelope.payload();

        log.info("""
                [HOUSEKEEPING-SIM] ────────────────────────────────────────
                  Ticket de preparación generado
                  Propiedad              : {}
                  Habitación             : {}
                  Huésped (check-in)     : {} — {}
                  Fecha check-in         : {}
                  Fecha check-out        : {}
                  Solicitudes especiales : {}
                  Operador responsable   : {}
                  eventId (trazabilidad) : {}
                ────────────────────────────────────────────────────────────
                """,
                payload.propertyName(),
                payload.roomNumber(),
                payload.guestName(),
                payload.guestEmail(),
                payload.checkIn(),
                payload.checkOut(),
                payload.specialRequests() != null ? payload.specialRequests() : "(ninguna)",
                payload.operatorEmail(),
                envelope.eventId()
        );
    }
}
