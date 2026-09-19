package com.rmsolutions.centinela.ingestion.redis;

import com.rmsolutions.centinela.ingestion.IngestProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Descarta en el borde un evento que ya se acepto, usando SETNX en Redis.
 *
 * <p>Ahorra a todo el pipeline el trabajo de procesar el reintento de un dispositivo
 * con mala cobertura. NO es la garantia de idempotencia: esa la da el indice unico
 * sobre dedup_key en PostgreSQL, que es quien tiene la ultima palabra.
 *
 * <p>Precisamente porque la base es la garantia real, aqui se puede fallar ABIERTO:
 * si Redis no responde, el evento pasa y el duplicado se descarta mas abajo.
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
@Slf4j
@Component
@Profile("!simulator")
@RequiredArgsConstructor
public class EdgeDeduplicator {

    private static final String PREFIX = "dedup:";

    private final StringRedisTemplate redis;
    private final IngestProperties props;

    /**
     * Marca el evento como visto.
     *
     * @return true si es la primera vez que se ve (hay que procesarlo); false si
     * ya se habia aceptado dentro de la ventana.
     */
    public boolean markAsSeen(String dedupKey) {
        try {
            Boolean first = redis.opsForValue().setIfAbsent(
                    PREFIX + dedupKey, "1", Duration.ofSeconds(props.dedupTtlSeconds()));
            return first == null || first;
        } catch (RuntimeException e) {
            log.error("Redis no responde al deduplicar; se DEJA PASAR el evento "
                    + "(la base descartara el duplicado) | causa={}", e.getMessage());
            return true;
        }
    }
}
