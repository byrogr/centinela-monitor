package com.rmsolutions.centinela.registry.application;

import java.util.UUID;

/**
 * Identidad de un dispositivo que acaba de autenticarse.
 *
 * <p>Se devuelve un record y no la entidad {@code Device} a proposito: la relacion
 * con el paciente es LAZY y, con 'open-in-view: false', leerla fuera de la
 * transaccion de verificacion lanzaria LazyInitializationException en cada
 * peticion del webhook. Resolviendo aqui lo que la ingesta necesita, el problema
 * no puede darse.
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
public record AuthenticatedDevice(
        UUID deviceId,
        UUID patientId,
        String patientCode, // Clave de particion en Kafka: garantiza orden por paciente.
        int silenceThresholdSeconds
) {
}
