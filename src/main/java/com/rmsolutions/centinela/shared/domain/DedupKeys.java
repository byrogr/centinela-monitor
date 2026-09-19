package com.rmsolutions.centinela.shared.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Calculo de la clave de deduplicacion de un evento.
 *
 * Vive en 'shared' y en un unico sitio A PROPOSITO: el consumidor de persistencia la calcula
 * al guardar y el webhook la calculara en el borde (tarea 7) para el SETNX de
 * Redis. Si cada uno la derivara por su cuenta y difirieran en un detalle, la
 * idempotencia se romperia sin que nada avisara.
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
public final class DedupKeys {

    private DedupKeys() {
    }

    /**
     * Deriva la clave de dispositivo + instante + estado de alarma.
     *
     * Se usa el epoch en milisegundos y no un texto con formato: un mismo instante
     * siempre produce la misma clave, sin depender de locale ni de zona horaria.
     */
    public static String of(UUID deviceId, Instant eventTime, int alarmState) {
        return deviceId + "|" + eventTime.toEpochMilli() + "|" + alarmState;
    }
}
