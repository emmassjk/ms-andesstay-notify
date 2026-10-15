package com.andesstay.notify;

import com.andesstay.notify.config.RabbitProperties;
import com.andesstay.notify.config.RabbitProperties.ChannelProps;
import com.andesstay.notify.event.ReservationEnvelope;
import com.andesstay.notify.event.ReservationPayload;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Datos de prueba compartidos por los tests de los listeners. */
public final class TestData {

    private TestData() {}

    public static ReservationEnvelope envelope(UUID eventId) {
        ReservationPayload payload = new ReservationPayload(
                1L, "Juan Pérez", "juan@example.com",
                "Hostal Andes", "101",
                LocalDate.now(), LocalDate.now().plusDays(3),
                new BigDecimal("50000"), "CLP",
                "operador@andesstay.cl", null
        );
        return new ReservationEnvelope("RESERVATION_CREATED", eventId, Instant.now(),
                "trace-1", "corr-1", payload);
    }

    /** RabbitProperties real (es un record, no se necesita mock). maxRetry = 3. */
    public static RabbitProperties rabbitProperties() {
        return new RabbitProperties(
                "cmd.direct", "cmd.topic", "cmd.dead.dlx",
                new ChannelProps("q.cmd.email", "q.cmd.email.dlq", "email.send", "email.*", "q.cmd.email.dlq"),
                new ChannelProps("q.cmd.housekeeping", "q.cmd.housekeeping.dlq", "housekeeping.ticket", "housekeeping.#", "q.cmd.housekeeping.dlq"),
                new ChannelProps("q.cmd.voucher", "q.cmd.voucher.dlq", "voucher.gen", "voucher.*", "q.cmd.voucher.dlq"),
                3
        );
    }
}
