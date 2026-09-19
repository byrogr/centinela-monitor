package com.rmsolutions.centinela.watchdog.domain;

import com.rmsolutions.centinela.ingestion.domain.AlarmState;
import com.rmsolutions.centinela.ingestion.domain.OsdEvent;

/**
 * Fabrica del evento que el vigilante inyecta cuando un reloj deja de emitir.
 *
 * <p>El vigilante NO notifica por su cuenta: construye un evento y lo mete por el
 * mismo pipeline que los reales. Asi la alerta de "se perdio la senal" recorre
 * exactamente el mismo camino, se clasifica con el mismo SeverityRouter y queda
 * en el historial como cualquier otro evento. Una via paralela de notificacion
 * seria codigo que solo se ejercita en la emergencia, justo cuando no puede fallar.
 *
 * <p>El evento se marca con dos senales que el router ya entiende:
 *   - alarmState UNKNOWN: no sabemos en que estado esta el nino, y eso escala.
 *   - watchConnected=false: no estamos recibiendo del reloj.
 * Ambas llevan a WARNING por separado, asi que la clasificacion no depende de una sola.
 *
 * @author Roger Rojas
 * @since 2026-09-19
 */
public final class SyntheticSilenceEvent {

    /** Permite reconocerlo en el historial y distinguirlo de un evento real de OSD. */
    public static final String PHRASE = "SILENCE";

    private SyntheticSilenceEvent() {
    }

    public static OsdEvent at(String osdTime) {
        return new OsdEvent(
                osdTime,
                AlarmState.UNKNOWN.code(),
                PHRASE,
                0.0, 0.0, 0.0, 0.0,
                null,   // sin pulso: no hay lectura, y un 0 se leeria como dato real
                null,   // sin bateria por el mismo motivo
                false); // el reloj no esta dando senal
    }
}
