-- =============================================================================
-- V2 - Tabla de eventos: el registro inmutable que alimentara el backoffice.
--
-- Decisiones:
--  * Particionada por rango mensual sobre event_time: el historial crece sin
--    limite y las consultas del backoffice siempre acotan por tiempo.
--  * Campos consultables en columnas + payload original completo en JSONB:
--    si OSD cambia de version, nada se pierde.
--  * dedup_key UNIQUE: la idempotencia se garantiza en la base, no en el codigo.
--  * Append-only reforzado por trigger: UPDATE y DELETE quedan prohibidos.
-- =============================================================================

CREATE TABLE event (
    id              uuid        NOT NULL DEFAULT gen_random_uuid(),
    device_id       uuid        NOT NULL REFERENCES device (id) ON DELETE RESTRICT,
    patient_id      uuid        NOT NULL REFERENCES patient (id) ON DELETE RESTRICT,
    event_time      timestamptz NOT NULL,
    alarm_state     int         NOT NULL,
    severity        text        NOT NULL CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
    heart_rate      int,
    battery_level   int CHECK (battery_level BETWEEN 0 AND 100),
    watch_connected boolean,
    raw_payload     jsonb       NOT NULL,
    dedup_key       text        NOT NULL,
    ingested_at     timestamptz NOT NULL DEFAULT now(),
    -- La clave de particion debe formar parte de toda restriccion unica.
    PRIMARY KEY (id, event_time),
    UNIQUE (dedup_key, event_time)
) PARTITION BY RANGE (event_time);

COMMENT ON TABLE event IS
    'Log append-only de eventos de OSD. Nunca se hace UPDATE ni DELETE sobre una fila.';
COMMENT ON COLUMN event.dedup_key IS
    'Derivada de device_id + event_time + alarm_state. Permite INSERT ... ON CONFLICT DO NOTHING.';
COMMENT ON COLUMN event.alarm_state IS
    'Codigo crudo de OSD (0=OK, 1=WARNING, 2=ALARM, 3=FALL...). Se guarda sin traducir: '
    'un codigo desconocido no debe perderse, y la severidad derivada va en su propia columna.';
COMMENT ON COLUMN event.raw_payload IS
    'Payload original completo de OSD, por si aparecen campos nuevos en otra version.';

-- Indices de las dos consultas del backoffice: historial de un paciente y de un dispositivo.
CREATE INDEX idx_event_patient_time ON event (patient_id, event_time DESC);
CREATE INDEX idx_event_device_time ON event (device_id, event_time DESC);

-- -----------------------------------------------------------------------------
-- Append-only a nivel de base de datos.
-- El trigger se define sobre la tabla particionada: Postgres lo propaga a todas
-- las particiones, incluidas las que se creen despues.
-- Nota: DROP de una particion (retencion) NO dispara el trigger, asi que la
-- politica de archivado a futuro sigue siendo posible.
-- -----------------------------------------------------------------------------
CREATE FUNCTION event_bloquear_mutacion() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'La tabla event es append-only: % no esta permitido', TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$;

CREATE TRIGGER event_append_only
    BEFORE UPDATE OR DELETE
    ON event
    FOR EACH ROW
EXECUTE FUNCTION event_bloquear_mutacion();

-- -----------------------------------------------------------------------------
-- Gestion de particiones mensuales.
-- -----------------------------------------------------------------------------
CREATE FUNCTION event_crear_particion(p_mes date) RETURNS text
    LANGUAGE plpgsql AS
$$
DECLARE
    v_inicio date := date_trunc('month', p_mes)::date;
    v_fin    date := (date_trunc('month', p_mes) + interval '1 month')::date;
    v_nombre text := 'event_' || to_char(v_inicio, 'YYYY_MM');
BEGIN
    IF to_regclass('public.' || v_nombre) IS NOT NULL THEN
        RETURN v_nombre || ' (ya existia)';
    END IF;

    EXECUTE format(
        'CREATE TABLE %I PARTITION OF event FOR VALUES FROM (%L) TO (%L)',
        v_nombre, v_inicio, v_fin);

    RETURN v_nombre || ' (creada)';
END;
$$;

COMMENT ON FUNCTION event_crear_particion(date) IS
    'Crea la particion mensual que contiene la fecha dada, si no existe. '
    'Idempotente: se puede llamar desde una tarea programada cada mes.';

-- Ventana inicial: mes anterior y los 12 siguientes. Se crean por adelantado
-- para que event_default se mantenga vacia (ver nota mas abajo).
DO
$$
    DECLARE
        v_mes date := (date_trunc('month', now()) - interval '1 month')::date;
    BEGIN
        FOR i IN 0..13
            LOOP
                PERFORM event_crear_particion(v_mes);
                v_mes := (v_mes + interval '1 month')::date;
            END LOOP;
    END;
$$;

-- Red de seguridad fail-safe: un evento con timestamp fuera de las particiones
-- creadas (reloj desfasado, backfill antiguo) se guarda aqui en vez de perderse.
-- CONTRAPARTIDA: si event_default acumula filas de un mes, crear despues la
-- particion de ese mes falla. Por eso las particiones se crean por adelantado y
-- conviene vigilar que event_default siga vacia.
CREATE TABLE event_default PARTITION OF event DEFAULT;
