package com.andesstay.notify.services;

import com.andesstay.notify.event.ReservationEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Servicio simulado de generación de voucher de reserva en PDF.
 * <p>
 * En producción, generaría un documento PDF con los detalles de la reserva
 * y lo almacenaría en el sistema de archivos o enviarlo por email al huésped.
 * <p>
 * Por ahora registra en logs el voucher que «generaría».
 */
@Service
public class VoucherService {

    private static final Logger log = LoggerFactory.getLogger(VoucherService.class);

    /**
     * Simula la generación del PDF de voucher para el huésped.
     *
     * @param envelope Envelope del evento de reserva.
     */
    public void generateVoucherPdf(ReservationEnvelope envelope) {
        var payload = envelope.payload();

        log.info("""
                [VOUCHER-SIM] ──────────────────────────────────────────────
                  PDF Voucher generado
                  Reserva #              : {}
                  Huésped                : {}
                  Propiedad              : {}
                  Habitación             : {}
                  Check-in               : {}
                  Check-out              : {}
                  Importe total          : {} {}
                  Nombre archivo         : voucher_{}_{}.pdf
                  eventId (trazabilidad) : {}
                ─────────────────────────────────────────────────────────────
                """,
                payload.reservationId(),
                payload.guestName(),
                payload.propertyName(),
                payload.roomNumber(),
                payload.checkIn(),
                payload.checkOut(),
                payload.totalAmount(),
                payload.currency(),
                payload.reservationId(),
                envelope.eventId(),
                envelope.eventId()
        );
    }
}
