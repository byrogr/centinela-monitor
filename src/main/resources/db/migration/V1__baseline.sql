-- =============================================================================
-- V1 - Modelo relacional base de Lenzo.
--
-- Principios aplicados:
--  * Privacidad del menor: se guarda 'birth_year', nunca la fecha de nacimiento.
--  * Solo hash de API keys: la clave en claro jamas toca la base de datos.
--  * Fail-safe: un cuidador sin ningun canal de contacto no es notificable,
--    asi que la base lo rechaza en vez de dejar una cadena de escalado rota.
-- =============================================================================

-- Familia: agrupa pacientes y cuidadores, y es la frontera de aislamiento de datos.
CREATE TABLE account (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name       text        NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

COMMENT ON TABLE account IS 'Familia o grupo que agrupa pacientes y cuidadores.';

-- Menor monitoreado.
CREATE TABLE patient (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id   uuid        NOT NULL REFERENCES account (id) ON DELETE RESTRICT,
    code         text        NOT NULL UNIQUE,
    display_name text        NOT NULL,
    birth_year   int CHECK (birth_year BETWEEN 1900 AND 2100),
    created_at   timestamptz NOT NULL DEFAULT now()
);

COMMENT ON COLUMN patient.code IS
    'Identificador estable del paciente (childId de la Fase 1). Es la clave de particion '
    'en Kafka, asi que el consumidor de persistencia lo usa para resolver el paciente.';
COMMENT ON COLUMN patient.birth_year IS
    'Solo el anio de nacimiento: minimizacion de datos de un menor. NUNCA la fecha exacta.';

CREATE INDEX idx_patient_account ON patient (account_id);

-- Cuidador que recibe las alertas.
CREATE TABLE caregiver (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id   uuid        NOT NULL REFERENCES account (id) ON DELETE RESTRICT,
    display_name text        NOT NULL,
    email        text,
    phone        text,
    created_at   timestamptz NOT NULL DEFAULT now(),
    -- Sin email ni telefono no hay forma de avisarle: no lo admitimos.
    CONSTRAINT caregiver_contact_present CHECK (email IS NOT NULL OR phone IS NOT NULL)
);

CREATE INDEX idx_caregiver_account ON caregiver (account_id);

-- Relacion N:M cuidador-paciente, con el orden de la cadena de escalado.
CREATE TABLE caregiver_link (
    caregiver_id     uuid        NOT NULL REFERENCES caregiver (id) ON DELETE CASCADE,
    patient_id       uuid        NOT NULL REFERENCES patient (id) ON DELETE CASCADE,
    escalation_order int         NOT NULL CHECK (escalation_order > 0),
    created_at       timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (caregiver_id, patient_id),
    -- Sin empates en el orden de escalado: 'a quien aviso primero' no puede ser ambiguo.
    -- DEFERRABLE para poder reordenar la cadena completa dentro de una transaccion.
    CONSTRAINT caregiver_link_unique_order UNIQUE (patient_id, escalation_order)
        DEFERRABLE INITIALLY IMMEDIATE
);

CREATE INDEX idx_caregiver_link_patient ON caregiver_link (patient_id, escalation_order);

-- Dispositivo que emite eventos (el celular con OSD emparejado al reloj).
CREATE TABLE device (
    id                        uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id                uuid        NOT NULL REFERENCES patient (id) ON DELETE RESTRICT,
    label                     text        NOT NULL,
    api_key_hash              text        NOT NULL UNIQUE,
    api_key_prefix            text        NOT NULL,
    silence_threshold_seconds int         NOT NULL DEFAULT 240
        CHECK (silence_threshold_seconds BETWEEN 60 AND 3600),
    active                    boolean     NOT NULL DEFAULT true,
    created_at                timestamptz NOT NULL DEFAULT now(),
    revoked_at                timestamptz
);

COMMENT ON COLUMN device.api_key_hash IS
    'Hash de la API key del dispositivo. La clave en claro se muestra UNA vez al emitirla.';
COMMENT ON COLUMN device.api_key_prefix IS
    'Primeros caracteres de la clave, para identificarla en la UI sin revelarla.';
COMMENT ON COLUMN device.silence_threshold_seconds IS
    'Segundos sin senal antes de que el vigilante abra una incidencia de silencio. '
    'Default 240s (4 min), dentro del rango 3-5 min acordado; afinar con datos reales.';

CREATE INDEX idx_device_patient ON device (patient_id);

-- Reglas de alerta por paciente y severidad.
CREATE TABLE alert_rule (
    id                     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id             uuid        NOT NULL REFERENCES patient (id) ON DELETE CASCADE,
    severity               text        NOT NULL CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
    escalate_after_seconds int         NOT NULL DEFAULT 60 CHECK (escalate_after_seconds >= 0),
    enabled                boolean     NOT NULL DEFAULT true,
    created_at             timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT alert_rule_unique_per_severity UNIQUE (patient_id, severity)
);

COMMENT ON COLUMN alert_rule.escalate_after_seconds IS
    'Segundos sin confirmacion antes de pasar al siguiente cuidador de la cadena (Fase 3).';
