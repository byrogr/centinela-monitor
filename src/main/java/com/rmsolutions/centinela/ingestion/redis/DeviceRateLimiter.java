package com.rmsolutions.centinela.ingestion.redis;

import com.rmsolutions.centinela.ingestion.IngestProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Limite de peticiones por dispositivo, con ventana fija de un minuto en Redis.
 *
 * FAIL-SAFE, Y AQUI SE ABRE: si Redis no responde, se DEJA PASAR el evento.
 *
 * Parece contradictorio con "ante la duda, escalar", pero es justo lo mismo: el
 * rate-limit es una proteccion frente a abusos, no un mecanismo de seguridad.
 * Si fallara cerrado, una caida de Redis convertiria un problema de infraestructura
 * en un apagon total de la monitorizacion: dejariamos de recibir los eventos del
 * nino por no poder contar peticiones. Se prefiere aceptar trafico de mas.
 *
 * (La autenticacion, en cambio, falla CERRADA: ver DeviceRegistryService.)
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
@Slf4j
@Component
@Profile("!simulator")
@RequiredArgsConstructor
public class DeviceRateLimiter {

    private static final String PREFIX = "ratelimit:device:";

    private final StringRedisTemplate redis;
    private final IngestProperties props;

    /**
     * @return true si el evento puede seguir; false solo si el dispositivo supero
     * su cuota y Redis lo confirmo.
     */
    public boolean allow(UUID deviceId) {
        String key = PREFIX + deviceId + ":" + Instant.now().getEpochSecond() / 60;
        try {
            Long count = redis.opsForValue().increment(key);
            if (count == null) {
                return true;
            }
            if (count == 1L) {
                // La ventana caduca sola; no hay tarea de limpieza que mantener.
                redis.expire(key, Duration.ofMinutes(2));
            }
            if (count > props.maxEventsPerMinute()) {
                log.warn("Rate limit superado | deviceId={} eventos={} tope={}",
                        deviceId, count, props.maxEventsPerMinute());
                return false;
            }
            return true;
        } catch (RuntimeException e) {
            log.error("Redis no responde al limitar trafico; se DEJA PASAR el evento "
                    + "para no dejar al nino sin monitorizacion | deviceId={} causa={}",
                    deviceId, e.getMessage());
            return true;
        }
    }
}
