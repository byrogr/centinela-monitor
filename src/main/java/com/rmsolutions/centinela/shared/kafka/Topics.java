package com.rmsolutions.centinela.shared.kafka;

/**
 * Nombres de los topicos. Estrategia de ruteo por severidad:<br>
 *
 *  - RAW:       TODOS los eventos (traza de auditoria / replay / analitica).<br>
 *  - TELEMETRY: eventos normales (latido, bateria OK, conectado).<br>
 *  - WARNING:   pre-ictal, bateria baja o reloj desconectado.<br>
 *  - CRITICAL:  convulsion / caida -> detona integraciones de emergencia.<br>
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
public final class Topics {

    public static final String RAW = "osd.events.raw";
    public static final String TELEMETRY = "osd.events.telemetry";
    public static final String WARNING = "osd.alerts.warning";
    public static final String CRITICAL = "osd.alerts.critical";

    private Topics() {
    }
}
