-- =============================================================================
-- V3 - Incidencias del vigilante de silencio.
--
-- Una incidencia se abre cuando un dispositivo lleva mas de
-- device.silence_threshold_seconds sin enviar senal, y se cierra cuando vuelve.
--
-- El vigilante NO notifica directamente: inyecta un evento sintetico al topico
-- de alertas y reutiliza el pipeline existente. Esta tabla es su memoria
-- durable (Redis solo guarda el last_seen, que es cache volatil).
-- =============================================================================

CREATE TABLE silence_incident (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id          uuid        NOT NULL REFERENCES device (id) ON DELETE RESTRICT,
    patient_id         uuid        NOT NULL REFERENCES patient (id) ON DELETE RESTRICT,
    -- Ultimo evento conocido antes del silencio; NULL si nunca hubo senal.
    last_event_time    timestamptz,
    threshold_seconds  int         NOT NULL CHECK (threshold_seconds > 0),
    opened_at          timestamptz NOT NULL DEFAULT now(),
    closed_at          timestamptz,
    -- Evento sintetico que se inyecto al pipeline al abrir la incidencia.
    -- Sirve para rastrear la alerta sin duplicarla en cada ciclo del scheduler.
    synthetic_dedup_key text,
    CONSTRAINT silence_incident_consistent_closure CHECK (closed_at IS NULL OR closed_at >= opened_at)
);

COMMENT ON TABLE silence_incident IS
    'Incidencias de perdida de senal. Abierta = closed_at IS NULL.';

-- Idempotencia del vigilante: como maximo UNA incidencia abierta por dispositivo.
-- Sin esto, cada ciclo del scheduler abriria una incidencia nueva y el cuidador
-- recibiria una alerta repetida cada pocos segundos.
CREATE UNIQUE INDEX idx_silence_incident_open_per_device
    ON silence_incident (device_id)
    WHERE closed_at IS NULL;

-- Historial de incidencias de un paciente para el backoffice.
CREATE INDEX idx_silence_incident_patient_time
    ON silence_incident (patient_id, opened_at DESC);
