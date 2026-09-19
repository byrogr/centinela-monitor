package com.rmsolutions.centinela.shared.persistence;

import com.rmsolutions.centinela.history.domain.Event;
import com.rmsolutions.centinela.history.persistence.EventRepository;
import com.rmsolutions.centinela.registry.domain.AlertRule;
import com.rmsolutions.centinela.registry.domain.CaregiverLink;
import com.rmsolutions.centinela.registry.domain.Device;
import com.rmsolutions.centinela.registry.domain.Patient;
import com.rmsolutions.centinela.registry.persistence.AlertRuleRepository;
import com.rmsolutions.centinela.registry.persistence.CaregiverLinkRepository;
import com.rmsolutions.centinela.registry.persistence.DeviceRepository;
import com.rmsolutions.centinela.registry.persistence.PatientRepository;
import com.rmsolutions.centinela.shared.domain.Severity;
import com.rmsolutions.centinela.watchdog.domain.SilenceIncident;
import com.rmsolutions.centinela.watchdog.persistence.SilenceIncidentRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de las entidades y repositorios contra un PostgreSQL real.
 *
 * El valor de estas pruebas no es solo ejercitar los metodos: al correr con
 * ddl-auto=validate sobre el esquema que crea Flyway, cualquier desajuste entre
 * una entidad y su tabla revienta aqui y no en produccion.
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        // Lo importante de esta prueba: Hibernate contrasta las entidades con el
        // esquema real de Flyway en vez de generarlo el mismo.
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.locations=classpath:db/migration,classpath:db/seed"
})
@Testcontainers
class PersistenciaJpaTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");

    @Autowired
    private PatientRepository patients;
    @Autowired
    private DeviceRepository devices;
    @Autowired
    private EventRepository events;
    @Autowired
    private SilenceIncidentRepository incidents;
    @Autowired
    private CaregiverLinkRepository links;
    @Autowired
    private AlertRuleRepository rules;

    private Patient paciente;
    private Device dispositivo;

    @BeforeEach
    void cargarSeed() {
        paciente = patients.findByCode("child-001").orElseThrow();
        dispositivo = devices.findByPatientId(paciente.getId()).getFirst();
    }

    @Test
    void elChildIdDeKafkaResuelveAlPaciente() {
        assertThat(paciente.getDisplayName()).isEqualTo("Paciente Demo");
        // Minimizacion de datos: solo el anio, nunca la fecha completa.
        assertThat(paciente.getBirthYear()).isEqualTo(2015);
    }

    @Test
    void elDispositivoSeEncuentraPorHashDeApiKeyYNuncaPorLaClaveEnClaro() {
        assertThat(devices.findByApiKeyHashAndActiveTrue(dispositivo.getApiKeyHash()))
                .isPresent();
        assertThat(devices.findByApiKeyHashAndActiveTrue("lenzo-dev-child-001-0123456789abcdef"))
                .as("la clave en claro no puede servir para autenticar: la base solo guarda el hash")
                .isEmpty();
    }

    @Test
    void insertarUnEventoEsIdempotente() {
        Instant cuando = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        String dedupKey = "dk-" + UUID.randomUUID();

        assertThat(insertar(cuando, dedupKey, 2, Severity.CRITICAL))
                .as("primer INSERT: el evento entra")
                .isEqualTo(1);

        assertThat(insertar(cuando, dedupKey, 2, Severity.CRITICAL))
                .as("mismo evento reprocesado: descartado, sin excepcion")
                .isEqualTo(0);

        List<Event> historial = events.historialDePaciente(
                paciente.getId(),
                cuando.minus(1, ChronoUnit.HOURS),
                cuando.plus(1, ChronoUnit.HOURS),
                PageRequest.of(0, 10));

        assertThat(historial).hasSize(1);
        assertThat(historial.getFirst().getDedupKey()).isEqualTo(dedupKey);
    }

    @Test
    void elEventoPersistidoConservaSeveridadYPayloadOriginal() {
        Instant cuando = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        String dedupKey = "dk-" + UUID.randomUUID();
        insertar(cuando, dedupKey, 3, Severity.CRITICAL);

        Event evento = events.ultimoEventoDeDispositivo(
                dispositivo.getId(), cuando.minus(1, ChronoUnit.HOURS)).orElseThrow();

        assertThat(evento.getSeverity()).isEqualTo(Severity.CRITICAL);
        assertThat(evento.getAlarmState()).as("el codigo crudo de OSD se guarda sin traducir").isEqualTo(3);
        assertThat(evento.getRawPayload()).contains("\"alarmPhrase\"");
        assertThat(evento.getEventTime()).isEqualTo(cuando);
        assertThat(evento.getIngestedAt()).as("lo pone la base, no el codigo").isNotNull();
    }

    @Test
    void soloHayUnaIncidenciaDeSilencioAbiertaPorDispositivo() {
        Instant ahora = Instant.now();
        incidents.saveAndFlush(new SilenceIncident(
                dispositivo.getId(), paciente.getId(), ahora.minusSeconds(300), 240, ahora));

        assertThat(incidents.findByDeviceIdAndClosedAtIsNull(dispositivo.getId())).isPresent();
        assertThat(incidents.findByClosedAtIsNull()).hasSize(1);

        SilenceIncident abierta = incidents.findByDeviceIdAndClosedAtIsNull(dispositivo.getId()).orElseThrow();
        abierta.cerrar(ahora);
        incidents.saveAndFlush(abierta);

        assertThat(incidents.findByClosedAtIsNull())
                .as("cerrada la incidencia, el dispositivo puede volver a vigilarse")
                .isEmpty();
    }

    @Test
    void laCadenaDeEscaladoLlegaOrdenada() {
        assertThat(links.findByIdPatientIdOrderByEscalationOrderAsc(paciente.getId()))
                .extracting(CaregiverLink::getEscalationOrder)
                .containsExactly(1, 2);
    }

    @Test
    void cadaSeveridadTieneSuReglaDeAlerta() {
        assertThat(rules.findByPatientId(paciente.getId())).hasSize(3);
        assertThat(rules.findByPatientIdAndSeverityAndEnabledTrue(paciente.getId(), Severity.CRITICAL))
                .isPresent()
                .get()
                .extracting(AlertRule::getEscalateAfterSeconds)
                .isEqualTo(60);
    }

    private int insertar(Instant cuando, String dedupKey, int alarmState, Severity severity) {
        return events.insertarSiNoExiste(
                dispositivo.getId(), paciente.getId(), cuando, alarmState, severity.name(),
                134, 88, true,
                "{\"alarmState\":%d,\"alarmPhrase\":\"ALARM\"}".formatted(alarmState),
                dedupKey);
    }
}
