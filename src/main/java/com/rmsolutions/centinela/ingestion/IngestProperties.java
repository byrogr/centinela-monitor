package com.rmsolutions.centinela.ingestion;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracion de la ingesta, enlazada desde 'app.ingest.*'.
 *
 * @param maxEventsPerMinute tope por dispositivo. OSD emite cada ~5s (12/min),
 *                           asi que el default deja un margen amplio: el limite
 *                           existe para frenar un bucle o un abuso, no para
 *                           estrechar el trafico legitimo.
 * @param dedupTtlSeconds    ventana en la que el borde recuerda un evento ya visto.
 *                           Cubre los reintentos del dispositivo, no la deduplicacion
 *                           definitiva, que la garantiza la base de datos.
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
@ConfigurationProperties(prefix = "app.ingest")
public record IngestProperties(
        int maxEventsPerMinute,
        int dedupTtlSeconds
) {
}
