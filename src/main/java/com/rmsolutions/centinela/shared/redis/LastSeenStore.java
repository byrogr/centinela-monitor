package com.rmsolutions.centinela.shared.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Ultimo instante en que se supo de cada dispositivo.
 *
 * Lo escribe la INGESTA en cuanto autentica al dispositivo, con el instante de
 * recepcion, y lo lee el vigilante de silencio para decidir si un reloj dejo de
 * emitir. Por eso vive en 'shared': es el punto de encuentro entre dos contextos,
 * no propiedad de ninguno.
 *
 * Se anota en la ingesta y no al persistir a proposito: si PostgreSQL se degrada,
 * los eventos siguen llegando al webhook y las alertas criticas siguen saliendo
 * por Kafka. Anotarlo en la persistencia dejaria la marca congelada durante esa
 * averia y el vigilante abriria incidencias de silencio para relojes que SI estan
 * emitiendo, justo cuando el camino de alerta funciona con normalidad.
 *
 * DOS DECISIONES QUE NO SON OBVIAS:
 *
 *  1. La marca solo AVANZA. Un reproceso del topico de Kafka entrega eventos
 *     antiguos, y escribirlos tal cual haria retroceder el last_seen: el vigilante
 *     creeria que el reloj lleva horas mudo y abriria una incidencia falsa justo
 *     cuando el dispositivo esta emitiendo con normalidad.
 *
 *  2. La marca NUNCA queda en el futuro. Un reloj mal configurado envia eventos
 *     fechados por delante, y anotarlos tal cual dejaria al vigilante creyendo que
 *     se supo del dispositivo dentro de nueve horas: no alarmaria jamas, por mucho
 *     que el reloj enmudezca. Es el fallo hacia el lado inseguro, asi que se acota
 *     al momento actual. El evento en si NO se descarta: una convulsion tiene que
 *     alertar aunque el reloj vaya desfasado.
 *
 *  3. Una escritura fallida NO rompe la persistencia. El evento ya esta guardado,
 *     que es lo irreemplazable; Redis aqui es cache. Si falla, el vigilante se
 *     queda sin dato reciente y debera resolverlo por su cuenta (fail-safe),
 *     pero el historial no se pierde ni el consumidor entra en bucle de reintentos.
 *
 * @author Roger Rojas
 * @since 2026-09-19
 */
@Slf4j
@Component
@Profile("!simulator")
@RequiredArgsConstructor
public class LastSeenStore {

    private static final String PREFIX = "lastseen:device:";

    /**
     * Caducidad generosa: acota el crecimiento sin estorbar. Si expira, el
     * vigilante no da por bueno el silencio: cae a su respaldo contra la base.
     */
    private static final Duration TTL = Duration.ofDays(7);

    /** Margen para desajustes normales de reloj; por encima se considera desfase. */
    private static final Duration CLOCK_TOLERANCE = Duration.ofMinutes(2);

    private final StringRedisTemplate redis;

    /**
     * Registra que se supo del dispositivo en ese instante, si es mas reciente
     * que lo que ya habia.
     *
     * No hace falta atomicidad de lectura-escritura: todos los eventos de un
     * paciente comparten clave de particion en Kafka, asi que los procesa un
     * unico consumidor en orden.
     */
    public void record(UUID deviceId, Instant when) {
        try {
            Instant now = Instant.now();
            if (when.isAfter(now.plus(CLOCK_TOLERANCE))) {
                log.warn("Evento fechado en el futuro: el reloj del dispositivo va desfasado "
                        + "| deviceId={} fecha={} ahora={}; se anota el momento actual",
                        deviceId, when, now);
                when = now;
            }

            String key = PREFIX + deviceId;
            String current = redis.opsForValue().get(key);

            if (current != null && Long.parseLong(current) >= when.toEpochMilli()) {
                return;
            }
            redis.opsForValue().set(key, Long.toString(when.toEpochMilli()), TTL);

        } catch (RuntimeException e) {
            log.error("No se pudo anotar el last_seen en Redis; el evento SI quedo guardado "
                    + "| deviceId={} causa={}", deviceId, e.getMessage());
        }
    }

    /**
     * @return el ultimo instante conocido, o vacio si Redis no lo sabe (nunca se
     * anoto, caduco o Redis no responde). Vacio significa DESCONOCIDO, nunca
     * "lleva mucho sin emitir": quien lo lea debe tratarlo como tal.
     */
    public Optional<Instant> lastSeen(UUID deviceId) {
        try {
            String value = redis.opsForValue().get(PREFIX + deviceId);
            return value == null
                    ? Optional.empty()
                    : Optional.of(Instant.ofEpochMilli(Long.parseLong(value)));

        } catch (RuntimeException e) {
            log.error("No se pudo leer el last_seen de Redis | deviceId={} causa={}",
                    deviceId, e.getMessage());
            return Optional.empty();
        }
    }
}
