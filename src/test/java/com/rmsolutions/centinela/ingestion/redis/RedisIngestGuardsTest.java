package com.rmsolutions.centinela.ingestion.redis;

import com.rmsolutions.centinela.ingestion.IngestProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;

import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de los guardias de borde contra un Redis real.
 *
 * La semantica de INCR y SETNX con TTL es justo lo que hace que estos guardias
 * funcionen, y no se puede simular con un mock sin acabar probando el mock.
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
class RedisIngestGuardsTest {

    // Misma version que docker-compose.yml.
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7.4.2").withExposedPorts(6379);

    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate redis;

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(
                REDIS.getHost(), REDIS.getMappedPort(6379)));
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
    }

    @AfterAll
    static void stopRedis() {
        factory.destroy();
        REDIS.stop();
    }

    private DeviceRateLimiter limiterWithQuota(int perMinute) {
        return new DeviceRateLimiter(redis, new IngestProperties(perMinute, 300));
    }

    @Test
    void trafficUnderTheQuotaIsAllowed() {
        DeviceRateLimiter limiter = limiterWithQuota(10);
        UUID device = UUID.randomUUID();

        assertThat(IntStream.range(0, 10).allMatch(i -> limiter.allow(device)))
                .as("el trafico legitimo de OSD no puede verse frenado")
                .isTrue();
    }

    @Test
    void trafficOverTheQuotaIsBlocked() {
        DeviceRateLimiter limiter = limiterWithQuota(5);
        UUID device = UUID.randomUUID();

        IntStream.range(0, 5).forEach(i -> limiter.allow(device));

        assertThat(limiter.allow(device)).isFalse();
    }

    @Test
    void theQuotaIsCountedPerDeviceAndNotGlobally() {
        DeviceRateLimiter limiter = limiterWithQuota(3);
        UUID noisy = UUID.randomUUID();
        UUID quiet = UUID.randomUUID();

        IntStream.range(0, 5).forEach(i -> limiter.allow(noisy));

        assertThat(limiter.allow(quiet))
                .as("un dispositivo desbocado no puede silenciar al de otro nino")
                .isTrue();
    }

    @Test
    void theFirstSightingOfAnEventPassesAndTheResendDoesNot() {
        EdgeDeduplicator deduplicator = new EdgeDeduplicator(redis, new IngestProperties(120, 300));
        // Clave unica por prueba: todas comparten el mismo Redis.
        String dedupKey = UUID.randomUUID() + "|1789999999999|2";

        assertThat(deduplicator.markAsSeen(dedupKey)).isTrue();
        assertThat(deduplicator.markAsSeen(dedupKey))
                .as("el reintento del dispositivo se corta antes de llegar a Kafka")
                .isFalse();
    }

    @Test
    void differentEventsDoNotInterfereWithEachOther() {
        EdgeDeduplicator deduplicator = new EdgeDeduplicator(redis, new IngestProperties(120, 300));

        String device = UUID.randomUUID().toString();

        assertThat(deduplicator.markAsSeen(device + "|1789999999999|0")).isTrue();
        assertThat(deduplicator.markAsSeen(device + "|1789999999999|2"))
                .as("mismo instante, otro estado de alarma: es otro evento")
                .isTrue();
        assertThat(deduplicator.markAsSeen(device + "|1789999999998|0"))
                .as("otro instante: tambien es otro evento")
                .isTrue();
    }

    @Test
    void theMemoryOfTheEdgeExpiresOnItsOwn() {
        EdgeDeduplicator deduplicator = new EdgeDeduplicator(redis, new IngestProperties(120, 1));
        String dedupKey = UUID.randomUUID() + "|1789999999997|2";

        assertThat(deduplicator.markAsSeen(dedupKey)).isTrue();
        assertThat(redis.getExpire("dedup:" + dedupKey))
                .as("sin TTL, Redis acumularia una clave por evento para siempre")
                .isPositive();
    }
}
