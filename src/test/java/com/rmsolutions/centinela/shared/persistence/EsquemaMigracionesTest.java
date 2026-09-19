package com.rmsolutions.centinela.shared.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifica que las migraciones de la Fase 2 producen un esquema que cumple los
 * principios no negociables. No basta con que Flyway corra sin error: lo que
 * importa es que la base IMPIDA romper la inmutabilidad y la idempotencia.
 *
 * Se levanta un PostgreSQL real con Testcontainers porque nada de esto
 * (particiones, triggers, indices parciales) existe en una base en memoria.
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
class EsquemaMigracionesTest {

    // Misma version que docker-compose.yml: probamos contra lo que corremos.
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17.6")
                    .withDatabaseName("centinela")
                    .withUsername("centinela")
                    .withPassword("centinela-test");

    private static Connection conn;

    @BeforeAll
    static void migrar() throws SQLException {
        POSTGRES.start();

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                // Incluimos el seed: las pruebas usan el dispositivo de desarrollo.
                .locations("classpath:db/migration", "classpath:db/seed")
                .load()
                .migrate();

        conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    @AfterAll
    static void cerrar() throws SQLException {
        if (conn != null) {
            conn.close();
        }
        POSTGRES.stop();
    }

    @Test
    void laTablaEventEstaParticionadaYTieneParticionPorDefecto() throws SQLException {
        assertThat(unEntero("""
                SELECT count(*) FROM pg_class c
                  JOIN pg_inherits i ON i.inhrelid = c.oid
                 WHERE i.inhparent = 'event'::regclass
                   AND c.relname <> 'event_default'
                """))
                .as("particiones mensuales creadas por adelantado")
                .isGreaterThanOrEqualTo(12);

        assertThat(unTexto("SELECT to_regclass('event_default')::text"))
                .as("particion por defecto: un evento fuera de rango no se pierde")
                .isEqualTo("event_default");
    }

    @Test
    void elEventoEsAppendOnly() throws SQLException {
        insertarEvento("dk-append-only");

        assertThatThrownBy(() -> ejecutar("UPDATE event SET heart_rate = 999 WHERE dedup_key = 'dk-append-only'"))
                .hasMessageContaining("append-only");

        assertThatThrownBy(() -> ejecutar("DELETE FROM event WHERE dedup_key = 'dk-append-only'"))
                .hasMessageContaining("append-only");
    }

    @Test
    void laDedupKeyImpideDuplicarUnEvento() throws SQLException {
        insertarEvento("dk-idempotente");
        insertarEvento("dk-idempotente");

        assertThat(unEntero("SELECT count(*) FROM event WHERE dedup_key = 'dk-idempotente'"))
                .as("el segundo INSERT no debe crear una fila nueva")
                .isEqualTo(1);
    }

    @Test
    void soloSePuedeTenerUnaIncidenciaDeSilencioAbiertaPorDispositivo() throws SQLException {
        String sql = """
                INSERT INTO silence_incident (device_id, patient_id, threshold_seconds)
                VALUES ('00000000-0000-0000-0000-0000000000d1',
                        '00000000-0000-0000-0000-0000000000b1', 240)
                """;
        ejecutar(sql);

        assertThatThrownBy(() -> ejecutar(sql))
                .as("sin esto el vigilante repetiria la alerta en cada ciclo")
                .hasMessageContaining("idx_silence_incident_abierta_por_device");

        // Al cerrarla, el dispositivo puede volver a entrar en silencio mas adelante.
        ejecutar("UPDATE silence_incident SET closed_at = now() WHERE closed_at IS NULL");
        ejecutar(sql);
    }

    @Test
    void elSeedGuardaSoloElHashDeLaApiKey() throws SQLException {
        assertThat(unTexto("SELECT api_key_hash FROM device LIMIT 1"))
                .as("la clave en claro no puede estar en la base")
                .isNotEqualTo("lenzo-dev-child-001-0123456789abcdef")
                .isEqualTo(unTexto("""
                        SELECT encode(sha256(convert_to('lenzo-dev-child-001-0123456789abcdef','UTF8')),'hex')
                        """));
    }

    @Test
    void elPacienteDeDesarrolloUsaElChildIdDeLaFase1() throws SQLException {
        assertThat(unTexto("SELECT code FROM patient LIMIT 1"))
                .as("es la clave de particion de Kafka; el consumidor la usa para resolver el paciente")
                .isEqualTo("child-001");
    }

    // ---- utilidades ----

    private void insertarEvento(String dedupKey) throws SQLException {
        ejecutar("""
                INSERT INTO event (device_id, patient_id, event_time, alarm_state, severity,
                                   heart_rate, battery_level, watch_connected, raw_payload, dedup_key)
                VALUES ('00000000-0000-0000-0000-0000000000d1',
                        '00000000-0000-0000-0000-0000000000b1',
                        '%s'::timestamptz, 2, 'CRITICAL', 134, 88, true,
                        '{"alarmState":2}'::jsonb, '%s')
                ON CONFLICT (dedup_key, event_time) DO NOTHING
                """.formatted("2026-09-18T12:00:00Z", dedupKey));
    }

    private void ejecutar(String sql) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    private int unEntero(String sql) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private String unTexto(String sql) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }
}
