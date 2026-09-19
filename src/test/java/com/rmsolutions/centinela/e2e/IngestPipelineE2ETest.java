package com.rmsolutions.centinela.e2e;

import com.rmsolutions.centinela.alerting.application.NotificationChannel;
import com.rmsolutions.centinela.history.domain.Event;
import com.rmsolutions.centinela.history.persistence.EventRepository;
import com.rmsolutions.centinela.ingestion.domain.OsdEvent;
import com.rmsolutions.centinela.registry.application.DeviceRegistryService;
import com.rmsolutions.centinela.registry.application.RegisteredDevice;
import com.rmsolutions.centinela.registry.domain.Patient;
import com.rmsolutions.centinela.registry.persistence.PatientRepository;
import com.rmsolutions.centinela.shared.domain.Severity;
import com.rmsolutions.centinela.shared.redis.LastSeenStore;
import com.rmsolutions.centinela.watchdog.application.SilenceWatchService;
import com.rmsolutions.centinela.watchdog.persistence.SilenceIncidentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Recorrido completo del pipeline con la infraestructura real levantada:
 * PostgreSQL, Kafka y Redis en contenedores.
 *
 * Es la prueba que ninguna otra cubre. Las demas verifican piezas contra una base
 * o un Redis reales, pero saltandose Kafka; aqui el evento entra por HTTP en el
 * webhook y hay que esperarlo al otro lado, despues de haber recorrido el broker,
 * el consumidor de persistencia y el de alertas. Es donde aparecen los fallos de
 * cableado que un test de unidad no puede ver: una cabecera que no viaja, un
 * deserializador mal configurado, un bean sin su @Profile.
 *
 * @author Roger Rojas
 * @since 2026-09-19
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class IngestPipelineE2ETest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.8.1");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7.4.2").withExposedPorts(6379);

    @DynamicPropertySource
    static void wireContainers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        // El vigilante no debe inyectar eventos sinteticos mientras se comprueba
        // lo que llega al historial: aqui se prueba la ingesta, no el silencio.
        registry.add("app.watchdog.initial-delay-ms", () -> "3600000");
    }

    /** Canal de notificacion que apunta lo recibido, para comprobar que la alerta llego. */
    static final List<Severity> NOTIFIED = new CopyOnWriteArrayList<>();

    @TestConfiguration
    static class RecordingChannel {
        @Bean
        NotificationChannel recordingChannel() {
            return new NotificationChannel() {
                @Override
                public void deliver(Severity severity, OsdEvent event) {
                    NOTIFIED.add(severity);
                }

                @Override
                public String name() {
                    return "test";
                }
            };
        }
    }

    private static final String SEED_KEY = "lenzo-dev-child-001-0123456789abcdef";

    @LocalServerPort
    private int port;
    @Autowired
    private EventRepository events;
    @Autowired
    private PatientRepository patients;
    @Autowired
    private DeviceRegistryService registry;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private LastSeenStore lastSeen;
    @Autowired
    private SilenceWatchService watch;
    @Autowired
    private SilenceIncidentRepository incidents;

    private RestClient http;
    private Patient patient;

    @BeforeEach
    void setUp() {
        http = RestClient.create("http://localhost:" + port);
        patient = patients.findByCode("child-001").orElseThrow();
        NOTIFIED.clear();
    }

    private String payload(String time, int alarmState) {
        return """
                {"Time":"%s","alarmState":%d,"alarmPhrase":"X","maxFreq":5.4,"maxVal":1250.3,\
                "specPower":4500.1,"roiPower":3800.8,"heartRate":134,"batteryLevel":88,\
                "watchConnected":true}""".formatted(time, alarmState);
    }

    private HttpStatusCode post(String apiKey, String body) {
        RestClient.RequestBodySpec request = http.post()
                .uri("/api/v1/osd/events")
                .contentType(MediaType.APPLICATION_JSON);
        if (apiKey != null) {
            request = request.header("X-Device-Key", apiKey);
        }
        return request.body(body)
                .retrieve()
                .onStatus(status -> true, (req, res) -> { })   // no lanzar por 4xx
                .toBodilessEntity()
                .getStatusCode();
    }

    private List<Event> history() {
        return events.findPatientHistory(
                patient.getId(),
                Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().plus(1, ChronoUnit.DAYS),
                PageRequest.of(0, 100));
    }

    /**
     * Eventos del historial cuyo payload lleva ese instante.
     *
     * Las pruebas afirman sobre SU evento y nunca sobre el total: los eventos de
     * otras pruebas siguen recorriendo Kafka de forma asincrona, asi que contar
     * filas seria una carrera perdida de antemano.
     */
    private List<Event> eventsAt(String time) {
        return history().stream()
                .filter(event -> event.getRawPayload().contains(time))
                .toList();
    }

    private String uniqueTime() {
        // Segundo distinto en cada prueba: la dedup_key incluye el instante.
        return "2026-09-19 %02d:%02d:%02d".formatted(
                (int) (System.nanoTime() % 20),
                (int) (System.nanoTime() / 61 % 60),
                (int) (System.nanoTime() / 7 % 60));
    }

    @Test
    void anEventTravelsFromTheWebhookToTheHistoryThroughKafka() {
        String time = uniqueTime();

        assertThat(post(SEED_KEY, payload(time, 2)).value()).isEqualTo(202);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(history())
                        .anySatisfy(event -> {
                            assertThat(event.getSeverity()).isEqualTo(Severity.CRITICAL);
                            assertThat(event.getPatientId()).isEqualTo(patient.getId());
                            assertThat(event.getDeviceId())
                                    .as("la cabecera del dispositivo tiene que haber viajado por Kafka")
                                    .isNotNull();
                            assertThat(event.getRawPayload()).contains(time);
                        }));
    }

    @Test
    void aCriticalEventReachesTheCaregiverAlertPath() {
        assertThat(post(SEED_KEY, payload(uniqueTime(), 2)).value()).isEqualTo(202);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(NOTIFIED)
                        .as("webhook -> Kafka -> AlertConsumer -> dispatcher -> canal")
                        .contains(Severity.CRITICAL));
    }

    @Test
    void anEventWithoutCredentialsNeverEntersThePipeline() {
        String anonymous = uniqueTime();
        String forged = uniqueTime();

        assertThat(post(null, payload(anonymous, 2)).value()).isEqualTo(401);
        assertThat(post("lenzo_inventada", payload(forged, 2)).value()).isEqualTo(401);

        assertThat(eventsAt(anonymous))
                .as("un desconocido no puede inyectar eventos en el historial de un nino")
                .isEmpty();
        assertThat(eventsAt(forged)).isEmpty();
    }

    @Test
    void aResentEventIsAcceptedButStoredOnlyOnce() {
        String time = uniqueTime();
        String body = payload(time, 2);

        assertThat(post(SEED_KEY, body).value()).isEqualTo(202);
        await().atMost(Duration.ofSeconds(30)).until(() -> eventsAt(time).size() == 1);

        assertThat(post(SEED_KEY, body).value())
                .as("para el dispositivo el evento ya se acepto")
                .isEqualTo(202);

        // Se sostiene la comprobacion un rato por si el duplicado se colara tarde.
        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(12))
                .untilAsserted(() -> assertThat(eventsAt(time))
                        .as("el reenvio no puede duplicar la fila")
                        .hasSize(1));
    }

    @Test
    void ingestingAnEventMarksTheDeviceAsSeen() {
        var device = registry.verify(SEED_KEY).orElseThrow();
        redis.delete("lastseen:device:" + device.deviceId());

        assertThat(post(SEED_KEY, payload(uniqueTime(), 0)).value()).isEqualTo(202);

        assertThat(redis.opsForValue().get("lastseen:device:" + device.deviceId()))
                .as("lo anota la ingesta, sin esperar a que el evento recorra Kafka")
                .isNotNull();
    }

    @Test
    void aRevokedDeviceIsLockedOutImmediately() {
        RegisteredDevice extra = registry.register(patient.getId(), "Celular E2E", 240);
        assertThat(post(extra.plainApiKey(), payload(uniqueTime(), 0)).value()).isEqualTo(202);

        registry.revoke(extra.deviceId());

        assertThat(post(extra.plainApiKey(), payload(uniqueTime(), 0)).value())
                .as("revocar tiene que cortar la ingesta sin reiniciar nada")
                .isEqualTo(401);
    }

    @Test
    void aSilentDeviceRaisesAnAlertThroughTheWholePipeline() {
        // Se registra un dispositivo propio y se le fabrica un silencio, para no
        // depender del estado que dejen las demas pruebas.
        RegisteredDevice silent = registry.register(patient.getId(), "Celular mudo", 60);
        lastSeen.record(silent.deviceId(), Instant.now().minus(10, ChronoUnit.MINUTES));

        assertThat(watch.scan()).isPositive();

        assertThat(incidents.findByDeviceIdAndClosedAtIsNull(silent.deviceId()))
                .as("la incidencia queda registrada de forma durable")
                .isPresent();

        // Lo que ninguna otra prueba cubre: el evento sintetico recorre Kafka de
        // verdad, lo clasifica el SeverityRouter y llega al canal de notificacion.
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(NOTIFIED)
                        .as("perder la senal tiene que avisar al cuidador, no solo anotarse")
                        .contains(Severity.WARNING));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(history())
                        .as("y queda en el historial como cualquier otro evento")
                        .anySatisfy(event -> {
                            assertThat(event.getDeviceId()).isEqualTo(silent.deviceId());
                            assertThat(event.getRawPayload()).contains("SILENCE");
                            assertThat(event.getSeverity()).isEqualTo(Severity.WARNING);
                        }));
    }

    @Test
    void anUnreadableTimestampIsRejectedWithoutPollutingTheHistory() {
        assertThat(post(SEED_KEY, payload("no es una fecha", 2)).value()).isEqualTo(422);

        assertThat(eventsAt("no es una fecha"))
                .as("guardarlo con una fecha inventada corromperia el historial")
                .isEmpty();
    }
}
