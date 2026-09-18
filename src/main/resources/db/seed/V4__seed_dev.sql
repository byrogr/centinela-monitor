-- =============================================================================
-- V4 - Datos de desarrollo. NO se aplica en produccion.
--
-- Vive en 'classpath:db/seed', una ubicacion de Flyway aparte de db/migration.
-- En local ambas ubicaciones estan activas; en produccion basta con dejar
-- FLYWAY_LOCATIONS=classpath:db/migration para que estos datos nunca se creen.
--
-- Todo es idempotente (ON CONFLICT DO NOTHING) y usa UUID fijos, para que el
-- seed se pueda reaplicar sobre una base existente sin duplicar nada.
-- =============================================================================

-- Familia de prueba.
INSERT INTO account (id, name)
VALUES ('00000000-0000-0000-0000-0000000000a1', 'Familia Demo')
ON CONFLICT (id) DO NOTHING;

-- Paciente. 'code' = child-001 es el mismo valor de app.child-id de la Fase 1,
-- que viaja como clave de particion en Kafka.
INSERT INTO patient (id, account_id, code, display_name, birth_year)
VALUES ('00000000-0000-0000-0000-0000000000b1',
        '00000000-0000-0000-0000-0000000000a1',
        'child-001', 'Paciente Demo', 2015)
ON CONFLICT (id) DO NOTHING;

-- Cadena de escalado: primero mama, luego el contacto de respaldo.
INSERT INTO caregiver (id, account_id, display_name, email, phone)
VALUES ('00000000-0000-0000-0000-0000000000c1',
        '00000000-0000-0000-0000-0000000000a1',
        'Cuidador Principal', 'cuidador1@example.test', '+00000000001'),
       ('00000000-0000-0000-0000-0000000000c2',
        '00000000-0000-0000-0000-0000000000a1',
        'Cuidador Respaldo', 'cuidador2@example.test', '+00000000002')
ON CONFLICT (id) DO NOTHING;

INSERT INTO caregiver_link (caregiver_id, patient_id, escalation_order)
VALUES ('00000000-0000-0000-0000-0000000000c1',
        '00000000-0000-0000-0000-0000000000b1', 1),
       ('00000000-0000-0000-0000-0000000000c2',
        '00000000-0000-0000-0000-0000000000b1', 2)
ON CONFLICT (caregiver_id, patient_id) DO NOTHING;

-- Dispositivo de desarrollo.
--
-- API KEY EN CLARO (solo dev): lenzo-dev-child-001-0123456789abcdef
-- La base guarda unicamente su SHA-256 en hexadecimal, calculado aqui mismo
-- para no dejar el hash "magico" pegado en el archivo.
--
-- OJO tarea 6: el verificador en Java debe usar exactamente este esquema
-- (SHA-256 del texto UTF-8, hex en minusculas). SHA-256 y no bcrypt a proposito:
-- la clave es un secreto de alta entropia generado por el sistema, y el webhook
-- esta en la ruta critica de una alerta; un hash lento por request costaria
-- latencia sin aportar seguridad real.
INSERT INTO device (id, patient_id, label, api_key_hash, api_key_prefix,
                    silence_threshold_seconds)
VALUES ('00000000-0000-0000-0000-0000000000d1',
        '00000000-0000-0000-0000-0000000000b1',
        'Celular demo + Garmin',
        encode(sha256(convert_to('lenzo-dev-child-001-0123456789abcdef', 'UTF8')), 'hex'),
        'lenzo-dev-ch',
        240)
ON CONFLICT (id) DO NOTHING;

-- Reglas de alerta por severidad.
INSERT INTO alert_rule (patient_id, severity, escalate_after_seconds)
VALUES ('00000000-0000-0000-0000-0000000000b1', 'CRITICAL', 60),
       ('00000000-0000-0000-0000-0000000000b1', 'WARNING', 300),
       ('00000000-0000-0000-0000-0000000000b1', 'INFO', 0)
ON CONFLICT (patient_id, severity) DO NOTHING;
