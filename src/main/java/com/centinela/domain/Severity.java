package com.centinela.domain;

/**
 * Nivel de severidad derivado, independiente del vocabulario de OSD.
 * Es lo que decide el ruteo y la accion de emergencia.
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
public enum Severity {
    INFO,       // Telemetria / latido normal.
    WARNING,    // Requiere atencion (pre-ictal, bateria baja, desconexion).
    CRITICAL    // Emergencia: convulsion o caida -> notificacion inmediata.
}
