package com.rmsolutions.centinela.shared.redis;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Pruebas del almacen de last_seen contra un Redis real.
 *
 * @author Roger Rojas
 * @since 2026-09-19
 */
class LastSeenStoreTest {

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7.4.2").withExposedPorts(6379);

    private static LettuceConnectionFactory factory;
    private static LastSeenStore store;
    private static StringRedisTemplate redis;

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(
                REDIS.getHost(), REDIS.getMappedPort(6379)));
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
        store = new LastSeenStore(redis);
    }

    @AfterAll
    static void stopRedis() {
        factory.destroy();
        REDIS.stop();
    }

    @Test
    void itRemembersWhenADeviceWasLastSeen() {
        UUID device = UUID.randomUUID();
        Instant when = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        store.record(device, when);

        assertThat(store.lastSeen(device)).contains(when);
    }

    @Test
    void anUnknownDeviceIsReportedAsUnknownAndNotAsSilent() {
        assertThat(store.lastSeen(UUID.randomUUID()))
                .as("vacio significa DESCONOCIDO; el vigilante no puede leerlo como silencio")
                .isEmpty();
    }

    @Test
    void theMarkMovesForwardWhenANewerEventArrives() {
        UUID device = UUID.randomUUID();
        Instant older = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant newer = older.plusSeconds(30);

        store.record(device, older);
        store.record(device, newer);

        assertThat(store.lastSeen(device)).contains(newer);
    }

    @Test
    void replayingAnOldEventDoesNotMoveTheMarkBackwards() {
        // Este es el caso que importa: reprocesar el topico de Kafka entrega
        // eventos antiguos. Si retrocedieran la marca, el vigilante abriria una
        // incidencia de silencio mientras el reloj esta emitiendo con normalidad.
        UUID device = UUID.randomUUID();
        Instant current = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant replayed = current.minus(6, ChronoUnit.HOURS);

        store.record(device, current);
        store.record(device, replayed);

        assertThat(store.lastSeen(device))
                .as("un replay no puede hacer creer que el dispositivo enmudecio")
                .contains(current);
    }

    @Test
    void aDeviceWithASkewedClockCannotPushTheMarkIntoTheFuture() {
        // Un reloj adelantado dejaria al vigilante creyendo que se supo del
        // dispositivo dentro de nueve horas: no alarmaria nunca. Es el fallo
        // hacia el lado inseguro, asi que la marca se acota al momento actual.
        UUID device = UUID.randomUUID();
        Instant skewed = Instant.now().plus(9, ChronoUnit.HOURS);

        store.record(device, skewed);

        assertThat(store.lastSeen(device))
                .isPresent()
                .get()
                .satisfies(mark -> assertThat(mark)
                        .as("la marca no puede quedar por delante del presente")
                        .isBeforeOrEqualTo(Instant.now().plusSeconds(1)));
    }

    @Test
    void aSkewedMarkDoesNotBlockLaterLegitimateEvents() {
        UUID device = UUID.randomUUID();
        store.record(device, Instant.now().plus(9, ChronoUnit.HOURS));

        Instant real = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        store.record(device, real);

        assertThat(store.lastSeen(device))
                .as("si la marca se hubiera quedado en el futuro, nada volveria a avanzarla")
                .isPresent();
    }

    @Test
    void theMarkExpiresOnItsOwn() {
        UUID device = UUID.randomUUID();
        store.record(device, Instant.now());

        assertThat(redis.getExpire("lastseen:device:" + device))
                .as("sin TTL, Redis acumularia una clave por dispositivo para siempre")
                .isPositive();
    }

    @Test
    void aRedisOutageNeitherThrowsNorInventsAnAnswer() {
        LettuceConnectionFactory caido = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration("localhost", 6390)); // puerto sin nadie
        caido.afterPropertiesSet();
        StringRedisTemplate roto = new StringRedisTemplate(caido);
        roto.afterPropertiesSet();
        LastSeenStore store = new LastSeenStore(roto);
        UUID device = UUID.randomUUID();

        // Escribir no puede tumbar la persistencia: el evento ya esta guardado.
        assertThatCode(() -> store.record(device, Instant.now())).doesNotThrowAnyException();
        // Y leer devuelve DESCONOCIDO, no un instante inventado.
        assertThat(store.lastSeen(device)).isEmpty();

        caido.destroy();
    }
}
