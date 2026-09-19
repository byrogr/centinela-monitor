package com.rmsolutions.centinela.history.application;

/**
 * Resultado de intentar guardar un evento.
 *
 * <p>Es un sealed interface por coherencia con {@code IngestOutcome}: los dos flujos
 * del pipeline reportan igual. Y sobre todo, obliga a quien llame a nombrar cada
 * desenlace en vez de mirar un boolean y adivinar por que fue false — un
 * duplicado descartado y un evento que no se pudo resolver no son lo mismo.
 *
 * @author Roger Rojas
 * @since 2026-09-19
 */
public sealed interface PersistOutcome {

    /** El evento entro en el historial. */
    record Persisted(String dedupKey) implements PersistOutcome {
    }

    /** Ya estaba: un reproceso o un reintento. No es un error. */
    record Duplicate(String dedupKey) implements PersistOutcome {
    }

    /**
     * No se pudo guardar por un problema de datos o de configuracion. El evento
     * sigue en Kafka y podra reprocesarse cuando se corrija.
     */
    record Discarded(String reason) implements PersistOutcome {
    }

    default boolean stored() {
        return this instanceof Persisted;
    }
}
