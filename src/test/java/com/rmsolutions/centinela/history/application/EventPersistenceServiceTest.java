package com.rmsolutions.centinela.history.application;

import com.rmsolutions.centinela.history.domain.Event;
import com.rmsolutions.centinela.history.persistence.EventRepository;
import com.rmsolutions.centinela.ingestion.domain.SeverityRouter;
import com.rmsolutions.centinela.registry.domain.Device;
import com.rmsolutions.centinela.registry.domain.Patient;
import com.rmsolutions.centinela.registry.persistence.DeviceRepository;
import com.rmsolutions.centinela.registry.persistence.PatientRepository;
import com.rmsolutions.centinela.shared.config.AppProperties;
import com.rmsolutions.centinela.shared.domain.OsdTimeParser;
import com.rmsolutions.centinela.shared.domain.Severity;
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
import static org.assertj.core.api.Assertions.assertThatCode;
import tools.jackson.databind.ObjectMapper;

/**
 * Pruebas del caso de uso de persistencia contra un PostgreSQL real.
 *
 * El servicio se prueba sin Kafka de por medio, que es justamente la ventaja de
 * haberlo separado del consumidor: aqui se verifica la logica (resolucion,
 * idempotencia, fidelidad del payload). El recorrido completo con Kafka es la
 * tarea 10.
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.locations=classpath:db/migration,classpath:db/seed"
})
@Testcontainers
class EventPersistenceServiceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");

    @Autowired
    private PatientRepository patients;
    @Autowired
    private DeviceRepository devices;
    @Autowired
    private EventRepository events;

    private EventPersistenceService service;
    private Patient patient;
    private Device device;

    @BeforeEach
    void buildService() {
        AppProperties props = new AppProperties(15, "America/Lima");
        service = new EventPersistenceService(
                patients, devices, events,
                new SeverityRouter(props),
                new OsdTimeParser(props),
                new ObjectMapper());
        patient = patients.findByCode("child-001").orElseThrow();
        device = devices.findByPatientId(patient.getId()).getFirst();
    }

    @Test
    void theDeviceHeaderIsWhatIdentifiesTheSender() {
        service.persist(payload("2026-09-18 00:05:32", 2, ""), "child-001",
                device.getId().toString());

        assertThat(history()).hasSize(1);
        assertThat(history().getFirst().getDeviceId())
                .as("el webhook ya autentico el dispositivo: no hay que adivinarlo")
                .isEqualTo(device.getId());
    }

    @Test
    void withoutTheHeaderItFallsBackToResolvingByPatient() {
        // Mensajes publicados antes de la tarea 7 siguen en el topico con su
        // retencion y tienen que poder reprocesarse.
        service.persist(payload("2026-09-18 00:05:32", 2, ""), "child-001", null);

        assertThat(history()).hasSize(1);
        assertThat(history().getFirst().getDeviceId()).isEqualTo(device.getId());
    }

    @Test
    void aMalformedDeviceHeaderPersistsNothing() {
        service.persist(payload("2026-09-18 00:05:32", 2, ""), "child-001", "no-es-un-uuid");

        assertThat(history())
                .as("atribuir el evento a otro dispositivo falsearia el historial")
                .isEmpty();
    }

    @Test
    void aHeaderPointingToAnUnknownDevicePersistsNothing() {
        service.persist(payload("2026-09-18 00:05:32", 2, ""), "child-001",
                UUID.randomUUID().toString());

        assertThat(history()).isEmpty();
    }

    private String payload(String time, int alarmState, String extra) {
        return """
                {"Time":"%s","alarmState":%d,"alarmPhrase":"ALARM","maxFreq":5.4,"maxVal":1250.3,\
                "specPower":4500.1,"roiPower":3800.8,"heartRate":134,"batteryLevel":88,\
                "watchConnected":true%s}""".formatted(time, alarmState, extra);
    }

    private List<Event> history() {
        return events.findPatientHistory(
                patient.getId(),
                Instant.now().minus(2, ChronoUnit.DAYS),
                Instant.now().plus(2, ChronoUnit.DAYS),
                PageRequest.of(0, 50));
    }

    @Test
    void itPersistsAnEventResolvingPatientAndDeviceFromTheChildId() {
        service.persist(payload("2026-09-18 00:05:32", 2, ""), "child-001", null);

        List<Event> stored = history();
        assertThat(stored).hasSize(1);

        Event e = stored.getFirst();
        assertThat(e.getPatientId()).isEqualTo(patient.getId());
        assertThat(e.getDeviceId()).isNotNull();
        assertThat(e.getAlarmState()).isEqualTo(2);
        assertThat(e.getHeartRate()).isEqualTo(134);
        // 00:05:32 en Lima (UTC-5) son las 05:05:32 UTC.
        assertThat(e.getEventTime()).isEqualTo(Instant.parse("2026-09-18T05:05:32Z"));
    }

    @Test
    void theStoredSeverityIsTheOneDecidedBySeverityRouter() {
        service.persist(payload("2026-09-18 00:05:32", 2, ""), "child-001", null);

        assertThat(history().getFirst().getSeverity())
                .as("lo que se guarda debe ser exactamente lo que se ruteo")
                .isEqualTo(Severity.CRITICAL);
    }

    @Test
    void reprocessingTheSameMessageDoesNotDuplicateTheEvent() {
        String message = payload("2026-09-18 00:05:32", 2, "");

        service.persist(message, "child-001", null);
        service.persist(message, "child-001", null);
        service.persist(message, "child-001", null);

        assertThat(history())
                .as("un rebalanceo o un replay de Kafka no puede inflar el history")
                .hasSize(1);
    }

    @Test
    void theRawPayloadKeepsFieldsTheDomainDoesNotKnow() {
        // OsdEvent declara @JsonIgnoreProperties(ignoreUnknown = true): si el consumidor
        // re-serializara el objeto deserializado, este campo se habria perdido.
        service.persist(payload("2026-09-18 00:05:32", 2, ",\"campoNuevoDeOsd\":\"valor\""), "child-001", null);

        assertThat(history().getFirst().getRawPayload())
                .as("raw_payload debe ser el JSON original, no una reconstruccion")
                .contains("campoNuevoDeOsd")
                .contains("valor");
    }

    @Test
    void anUnknownChildIdPersistsNothingAndThrowsNoException() {
        assertThatCode(() -> service.persist(payload("2026-09-18 00:05:32", 2, ""), "child-missing", null))
                .as("bloquear la particion detendria la persistencia de todos los eventos")
                .doesNotThrowAnyException();

        assertThat(history()).isEmpty();
    }

    @Test
    void anUnreadableTimeDoesNotPersistAnInventedDate() {
        service.persist(payload("no es una fecha", 2, ""), "child-001", null);

        assertThat(history())
                .as("mejor no guardarlo que guardarlo con una fecha falsa que rompa la deduplicacion")
                .isEmpty();
    }

    @Test
    void aNonJsonPayloadThrowsNoException() {
        assertThatCode(() -> service.persist("{esto no es json", "child-001", null))
                .doesNotThrowAnyException();

        assertThat(history()).isEmpty();
    }
}
