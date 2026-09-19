package com.rmsolutions.centinela.ingestion.application;

import com.rmsolutions.centinela.shared.domain.Severity;

/**
 * Resultado de intentar ingerir un evento. El controlador lo traduce a HTTP.
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
public sealed interface IngestOutcome {

    /** Evento aceptado y publicado en Kafka. */
    record Accepted(Severity severity, java.util.List<String> topics, String dedupKey)
            implements IngestOutcome {
    }

    /**
     * El dispositivo reenvio un evento que ya se acepto. Para el, la operacion
     * fue un exito: no hay nada que reintentar.
     */
    record Duplicate(String dedupKey) implements IngestOutcome {
    }

    /** Credencial ausente, desconocida o revocada. */
    record Unauthorized() implements IngestOutcome {
    }

    /** El dispositivo supero su cuota de peticiones. */
    record RateLimited() implements IngestOutcome {
    }

    /** El payload no permite identificar el momento del evento. */
    record Unprocessable(String reason) implements IngestOutcome {
    }
}
