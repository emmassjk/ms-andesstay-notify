package com.andesstay.notify.security;

import java.time.Instant;

/**
 * Cuerpo de respuesta estándar para errores de autenticación (401) y
 * autorización (403).
 * <p>
 * Replica el formato de {@code ProblemDetail} de Spring pero simplificado:
 * timestamp, código HTTP, nombre del error y mensaje descriptivo.
 *
 * @param timestamp Momento en que se produjo el error.
 * @param status    Código HTTP (401, 403, etc.).
 * @param error     Nombre corto del error (Unauthorized, Forbidden).
 * @param message   Descripción legible del problema.
 * @param path      URI de la petición que lo originó.
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {}

