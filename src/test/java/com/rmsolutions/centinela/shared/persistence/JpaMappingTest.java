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
 * <p>El valor de estas pruebas no es solo ejercitar los metodos: al correr con
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
class JpaMappingTest {

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

    private Patient patient;
    private Device device;

    @BeforeEach
    void loadSeed() {
        patient = patients.findByCode("child-001").orElseThrow();
        device = devices.findByPatientId(patient.getId()).getFirst();
    }

    @Test
    void theKafkaChildIdResolvesToThePatient() {
        assertThat(patient.getDisplayName()).isEqualTo("Paciente Demo");
        // Minimizacion de datos: solo el anio, nunca la fecha completa.
        assertThat(patient.getBirthYear()).isEqualTo(2015);
    }

    @Test
    void theDeviceIsFoundByApiKeyHashAndNeverByThePlainKey() {
        assertThat(devices.findByApiKeyHashAndActiveTrue(device.getApiKeyHash()))
                .isPresent();
        assertThat(devices.findByApiKeyHashAndActiveTrue("lenzo-dev-child-001-0123456789abcdef"))
                .as("la key en claro no puede servir para autenticar: la base solo guarda el hash")
                .isEmpty();
    }

    @Test
    void insertingAnEventIsIdempotent() {
        Instant when = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        String dedupKey = "dk-" + UUID.randomUUID();

        assertThat(insert(when, dedupKey, 2, Severity.CRITICAL))
                .as("primer INSERT: el evento entra")
                .isEqualTo(1);

        assertThat(insert(when, dedupKey, 2, Severity.CRITICAL))
                .as("mismo evento reprocesado: descartado, sin excepcion")
                .isEqualTo(0);

        List<Event> history = events.findPatientHistory(
                patient.getId(),
                when.minus(1, ChronoUnit.HOURS),
                when.plus(1, ChronoUnit.HOURS),
                PageRequest.of(0, 10));

        assertThat(history).hasSize(1);
        assertThat(history.getFirst().getDedupKey()).isEqualTo(dedupKey);
    }

    @Test
    void thePersistedEventKeepsSeverityAndOriginalPayload() {
        Instant when = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        String dedupKey = "dk-" + UUID.randomUUID();
        insert(when, dedupKey, 3, Severity.CRITICAL);

        Event evento = events.findLastDeviceEvent(
                device.getId(), when.minus(1, ChronoUnit.HOURS)).orElseThrow();

        assertThat(evento.getSeverity()).isEqualTo(Severity.CRITICAL);
        assertThat(evento.getAlarmState()).as("el codigo crudo de OSD se guarda sin traducir").isEqualTo(3);
        assertThat(evento.getRawPayload()).contains("\"alarmPhrase\"");
        assertThat(evento.getEventTime()).isEqualTo(when);
        assertThat(evento.getIngestedAt()).as("lo pone la base, no el codigo").isNotNull();
    }

    @Test
    void onlyOneOpenSilenceIncidentPerDevice() {
        Instant now = Instant.now();
        incidents.saveAndFlush(new SilenceIncident(
                device.getId(), patient.getId(), now.minusSeconds(300), 240, now));

        assertThat(incidents.findByDeviceIdAndClosedAtIsNull(device.getId())).isPresent();
        assertThat(incidents.findByClosedAtIsNull()).hasSize(1);

        SilenceIncident open = incidents.findByDeviceIdAndClosedAtIsNull(device.getId()).orElseThrow();
        open.close(now);
        incidents.saveAndFlush(open);

        assertThat(incidents.findByClosedAtIsNull())
                .as("cerrada la incidencia, el device puede volver a vigilarse")
                .isEmpty();
    }

    @Test
    void theEscalationChainComesOrdered() {
        assertThat(links.findByIdPatientIdOrderByEscalationOrderAsc(patient.getId()))
                .extracting(CaregiverLink::getEscalationOrder)
                .containsExactly(1, 2);
    }

    @Test
    void everySeverityHasItsAlertRule() {
        assertThat(rules.findByPatientId(patient.getId())).hasSize(3);
        assertThat(rules.findByPatientIdAndSeverityAndEnabledTrue(patient.getId(), Severity.CRITICAL))
                .isPresent()
                .get()
                .extracting(AlertRule::getEscalateAfterSeconds)
                .isEqualTo(60);
    }

    private int insert(Instant when, String dedupKey, int alarmState, Severity severity) {
        return events.insertIfAbsent(
                device.getId(), patient.getId(), when, alarmState, severity.name(),
                134, 88, true,
                "{\"alarmState\":%d,\"alarmPhrase\":\"ALARM\"}".formatted(alarmState),
                dedupKey);
    }
}
