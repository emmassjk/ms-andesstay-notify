package com.andesstay.notify.model;

/**
 * Constantes reutilizables del dominio de notificaciones.
 * <p>
 * Centraliza valores que se usan en múltiples clases (controladores,
 * servicios, listeners) para evitar strings mágicos dispersos.
 */
public final class NotifyConstants {

    private NotifyConstants() {
        // Utility class — no instanciable
    }

    // ─── Prefijos de log por canal ───────────────────────────────────────────
    public static final String LOG_PREFIX_EMAIL        = "[EMAIL]";
    public static final String LOG_PREFIX_HOUSEKEEPING = "[HOUSEKEEPING]";
    public static final String LOG_PREFIX_VOUCHER      = "[VOUCHER]";
    public static final String LOG_PREFIX_DLQ          = "[DLQ]";

    // ─── Rutas de la API REST ────────────────────────────────────────────────
    public static final String API_BASE         = "/api/v1/notificaciones";
    public static final String API_BY_CANAL     = "/canal/{canal}";
    public static final String API_BY_ESTADO    = "/estado/{estado}";
    public static final String API_BY_RESERVA   = "/reserva/{reservationId}";

    // ─── Mensajes de error estándar ──────────────────────────────────────────
    public static final String ERR_NOT_FOUND    = "Notificación no encontrada con id: ";
    public static final String ERR_CANAL_INV    = "Canal de notificación inválido: ";
    public static final String ERR_ESTADO_INV   = "Estado de notificación inválido: ";
}
