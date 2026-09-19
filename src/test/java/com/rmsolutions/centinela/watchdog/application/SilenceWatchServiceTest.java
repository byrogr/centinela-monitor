package com.rmsolutions.centinela.watchdog.application;

import com.rmsolutions.centinela.history.persistence.EventRepository;
import com.rmsolutions.centinela.ingestion.domain.OsdEvent;
import com.rmsolutions.centinela.ingestion.messaging.EventRoutingProducer;
import com.rmsolutions.centinela.registry.domain.Device;
import com.rmsolutions.centinela.registry.domain.Patient;
import com.rmsolutions.centinela.registry.persistence.DeviceRepository;
import com.rmsolutions.centinela.registry.persistence.PatientRepository;
import com.rmsolutions.centinela.shared.config.AppProperties;
import com.rmsolutions.centinela.shared.domain.OsdTimeParser;
import com.rmsolutions.centinela.shared.redis.LastSeenStore;
import com.rmsolutions.centinela.watchdog.domain.SilenceIncident;
import com.rmsolutions.centinela.watchdog.domain.SyntheticSilenceEvent;
import com.rmsolutions.centinela.watchdog.persistence.SilenceIncidentRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Pruebas del vigilante de silencio contra PostgreSQL y Redis reales.
 *
 * El estado del vigilante vive repartido entre los dos (incidencia durable en la
 * base, last_seen volatil en Redis) y el indice unico parcial que impide avisos
 * repetidos es una garantia de la base: con mocks no se estaria probando nada.
 *
 * @author Roger Rojas
 * @since 2026-09-19
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.locations=classpath:db/migration,classpath:db/seed"
})
@Testcontainers
class SilenceWatchServiceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");

    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7.4.2").withExposedPorts(6379);

    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate redis;
    private static LastSeenStore lastSeen;

    @Autowired
    private DeviceRepository devices;
    @Autowired
    private PatientRepository patients;
    @Autowired
    private EventRepository events;
    @Autowired
    private SilenceIncidentRepository incidents;

    private EventRoutingProducer producer;
    private SilenceWatchService watch;
    private Device device;
    private Patient patient;

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(
                REDIS.getHost(), REDIS.getMappedPort(6379)));
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
        lastSeen = new LastSeenStore(redis);
    }

    @AfterAll
    static void stopRedis() {
        factory.destroy();
        REDIS.stop();
    }

    @BeforeEach
    void setUp() {
        producer = mock(EventRoutingProducer.class);
        watch = new SilenceWatchService(
                devices, incidents, events, lastSeen, producer,
                new OsdTimeParser(new AppProperties(15, "America/Lima")));
        patient = patients.findByCode("child-001").orElseThrow();
        device = devices.findByPatientId(patient.getId()).getFirst();
        // Redis no participa del rollback de @DataJpaTest: se limpia a mano.
        redis.delete("lastseen:device:" + device.getId());
    }

    /**
     * Fuerza la marca de last_seen a un momento concreto.
     *
     * Hay que borrar la clave antes: el almacen es monotonico a proposito (no deja
     * retroceder la marca) y las pruebas comparten Redis y dispositivo, asi que sin
     * esto una prueba anterior impediria a la siguiente simular un silencio.
     */
    private void seenAgo(long seconds) {
        redis.delete("lastseen:device:" + device.getId());
        lastSeen.record(device.getId(), Instant.now().minusSeconds(seconds));
    }

    @Test
    void aDeviceThatKeepsSendingRaisesNoIncident() {
        seenAgo(10);

        assertThat(watch.scan()).isZero();
        assertThat(incidents.findByClosedAtIsNull()).isEmpty();
        verify(producer, never()).publish(any(), any(), anyString());
    }

    @Test
    void silenceBeyondTheThresholdOpensAnIncident() {
        seenAgo(device.getSilenceThresholdSeconds() + 60);

        assertThat(watch.scan()).isEqualTo(1);

        assertThat(incidents.findByDeviceIdAndClosedAtIsNull(device.getId()))
                .isPresent()
                .get()
                .satisfies(i -> {
                    assertThat(i.getPatientId()).isEqualTo(patient.getId());
                    assertThat(i.getThresholdSeconds())
                            .isEqualTo(device.getSilenceThresholdSeconds());
                    assertThat(i.getLastEventTime()).isNotNull();
                });
    }

    @Test
    void theIncidentInjectsASyntheticEventIntoThePipeline() {
        seenAgo(device.getSilenceThresholdSeconds() + 60);
        watch.scan();

        ArgumentCaptor<OsdEvent> captured = ArgumentCaptor.forClass(OsdEvent.class);
        verify(producer).publish(captured.capture(), eq(device.getId()), eq("child-001"));

        OsdEvent synthetic = captured.getValue();
        assertThat(synthetic.alarmPhrase()).isEqualTo(SyntheticSilenceEvent.PHRASE);
        assertThat(synthetic.watchConnected())
                .as("es la senal que hace al SeverityRouter escalar a WARNING")
                .isFalse();
        assertThat(synthetic.heartRate())
                .as("no hay lectura: un 0 se leeria como un pulso real de cero")
                .isNull();
    }

    @Test
    void theAlertIsNotRepeatedOnEveryPass() {
        seenAgo(device.getSilenceThresholdSeconds() + 60);

        watch.scan();
        watch.scan();
        watch.scan();

        assertThat(incidents.findByClosedAtIsNull())
                .as("el indice unico parcial garantiza una sola incidencia abierta")
                .hasSize(1);
        verify(producer).publish(any(), any(), anyString());
    }

    @Test
    void theIncidentClosesWhenTheSignalComesBack() {
        seenAgo(device.getSilenceThresholdSeconds() + 60);
        watch.scan();
        assertThat(incidents.findByClosedAtIsNull()).hasSize(1);

        seenAgo(5);
        assertThat(watch.scan()).isZero();

        assertThat(incidents.findByClosedAtIsNull()).isEmpty();
        assertThat(incidents.findByDeviceIdAndClosedAtIsNull(device.getId())).isEmpty();
    }

    @Test
    void aDeviceCanFallSilentAgainAfterRecovering() {
        seenAgo(device.getSilenceThresholdSeconds() + 60);
        watch.scan();
        seenAgo(5);
        watch.scan();

        seenAgo(device.getSilenceThresholdSeconds() + 60);

        assertThat(watch.scan())
                .as("cerrada la anterior, el dispositivo vuelve a ser vigilable")
                .isEqualTo(1);
        assertThat(incidents.findByClosedAtIsNull()).hasSize(1);
    }

    @Test
    void aSkewedClockCannotConvinceTheWatchdogThatAllIsWell() {
        // Si la marca futura se tomara al pie de la letra, la resta daria un
        // silencio negativo y el vigilante no alarmaria jamas.
        lastSeen.record(device.getId(), Instant.now().plus(9, ChronoUnit.HOURS));

        // La marca se acoto al presente al escribirla, asi que no hay silencio aun.
        assertThat(watch.scan()).isZero();
    }

    @Test
    void aRevokedDeviceIsNoLongerWatched() {
        device.revoke(Instant.now());
        devices.saveAndFlush(device);

        seenAgo(device.getSilenceThresholdSeconds() + 600);

        assertThat(watch.scan())
                .as("un dispositivo dado de baja no puede generar alertas eternas")
                .isZero();
    }

    @Test
    void anIncidentWhoseSyntheticEventNeverWentOutIsRetried() {
        seenAgo(device.getSilenceThresholdSeconds() + 60);
        // Se simula que la publicacion anterior fallo: la incidencia quedo abierta
        // pero sin evento sintetico, asi que el cuidador nunca fue avisado.
        incidents.saveAndFlush(new SilenceIncident(
                device.getId(), patient.getId(), Instant.now().minusSeconds(600), 240,
                Instant.now().minusSeconds(300)));

        watch.scan();

        verify(producer).publish(any(), eq(device.getId()), anyString());
        assertThat(incidents.findByDeviceIdAndClosedAtIsNull(device.getId()))
                .get()
                .extracting(SilenceIncident::getSyntheticDedupKey)
                .isNotNull();
    }

    @Test
    void aDeviceThatNeverReportedIsWatchedFromItsRegistrationDate() {
        Device fresh = devices.saveAndFlush(new Device(
                patient, "Recien registrado", "hash-" + UUID.randomUUID(), "lenzo_nuevo", 240));

        // Recien dado de alta y sin senal: todavia dentro de su margen.
        assertThat(incidents.findByDeviceIdAndClosedAtIsNull(fresh.getId())).isEmpty();
        watch.scan();
        assertThat(incidents.findByDeviceIdAndClosedAtIsNull(fresh.getId()))
                .as("no se alarma en el instante de registrar un dispositivo")
                .isEmpty();
    }
}
