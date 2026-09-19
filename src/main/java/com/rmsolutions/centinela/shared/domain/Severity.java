package com.rmsolutions.centinela.shared.domain;

/**
 * Nivel de severidad derivado, independiente del vocabulario de OSD.
 * Es lo que decide el ruteo y la accion de emergencia.
 *
 * Vive en 'shared' y no en 'ingestion' porque es vocabulario COMPARTIDO: lo usan
 * la ingesta al clasificar, el historial al guardar y el registro en sus reglas
 * de alerta. Tenerlo en ingestion obligaba a registry a depender del pipeline de
 * ingesta, una relacion que no significa nada en terminos de negocio.
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
public enum Severity {
    INFO,       // Telemetria / latido normal.
    WARNING,    // Requiere atencion (pre-ictal, bateria baja, desconexion).
    CRITICAL    // Emergencia: convulsion o caida -> notificacion inmediata.
}
