package com.rmsolutions.centinela.watchdog.application;

import com.rmsolutions.centinela.history.persistence.EventRepository;
import com.rmsolutions.centinela.ingestion.domain.OsdEvent;
import com.rmsolutions.centinela.ingestion.messaging.EventRoutingProducer;
import com.rmsolutions.centinela.registry.domain.Device;
import com.rmsolutions.centinela.registry.persistence.DeviceRepository;
import com.rmsolutions.centinela.shared.domain.DedupKeys;
import com.rmsolutions.centinela.shared.domain.OsdTimeParser;
import com.rmsolutions.centinela.shared.redis.LastSeenStore;
import com.rmsolutions.centinela.watchdog.domain.SilenceIncident;
import com.rmsolutions.centinela.watchdog.domain.SyntheticSilenceEvent;
import com.rmsolutions.centinela.watchdog.persistence.SilenceIncidentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Vigilante de silencio: avisa cuando un reloj deja de dar senal.
 * <p>
 * Es la otra mitad del sistema. Detectar una convulsion sirve de poco si el reloj
 * se apaga, se queda sin bateria o pierde el emparejamiento y nadie se entera:
 * el sistema pareceria estar funcionando mientras el nino esta sin supervision.
 * <p>
 * COMO SE DECIDE QUE HAY SILENCIO
 * Se busca la ultima senal en tres sitios, en orden de frescura:
 *   1. Redis (last_seen), que anota la ingesta al recibir cada evento.
 *   2. La base, por si Redis esta caido o frio: el historial no miente.
 *   3. La fecha de alta del dispositivo, para un reloj que nunca llego a emitir.
 * <p>
 * El tercer paso importa mas de lo que parece: sin el, un dispositivo que nunca
 * conecto quedaria fuera de la vigilancia para siempre, que es precisamente el
 * fallo silencioso que este componente existe para evitar. Y usar su fecha de alta
 * como referencia le da el mismo margen que a cualquier otro, sin alarmar al
 * instante de registrarlo.
 *
 * @author Roger Rojas
 * @since 2026-09-19
 */
@Slf4j
@Service
@Profile("!simulator")
@RequiredArgsConstructor
public class SilenceWatchService {

    /** Cuanto historial mira el respaldo contra la base. */
    private static final Duration DB_LOOKBACK = Duration.ofDays(2);

    private final DeviceRepository devices;
    private final SilenceIncidentRepository incidents;
    private final EventRepository events;
    private final LastSeenStore lastSeen;
    private final EventRoutingProducer producer;
    private final OsdTimeParser timeParser;

    /**
     * Revisa todos los dispositivos activos. Lo llama el scheduler.
     *
     * @return cuantas incidencias se abrieron en esta pasada.
     */
    @Transactional
    public int scan() {
        Instant now = Instant.now();
        int opened = 0;

        for (Device device : devices.findByActiveTrue()) {
            try {
                if (review(device, now)) {
                    opened++;
                }
            } catch (RuntimeException e) {
                // Un dispositivo problematico no puede dejar sin vigilar a los demas.
                log.error("Fallo revisando el dispositivo {} | causa={}",
                        device.getId(), e.getMessage(), e);
            }
        }
        return opened;
    }

    /** @return true si se abrio una incidencia nueva. */
    private boolean review(Device device, Instant now) {
        Instant reference = lastKnownSignal(device, now);
        Duration silence = Duration.between(reference, now);
        boolean silent = silence.getSeconds() > device.getSilenceThresholdSeconds();

        Optional<SilenceIncident> open = incidents.findByDeviceIdAndClosedAtIsNull(device.getId());

        if (!silent) {
            open.ifPresent(incident -> close(incident, device, now));
            return false;
        }

        if (open.isPresent()) {
            // Ya avisamos. Solo se reintenta si el evento sintetico no llego a salir,
            // para que un fallo puntual de Kafka no deje la incidencia muda.
            if (open.get().getSyntheticDedupKey() == null) {
                log.warn("La incidencia {} no tiene evento sintetico; se reintenta",
                        open.get().getId());
                inject(open.get(), device, now);
            }
            return false;
        }

        return open(device, reference, silence, now);
    }

    /**
     * Ultima senal conocida. Nunca devuelve un instante futuro: un reloj
     * desajustado no puede hacer creer al vigilante que ya se le oyo.
     */
    private Instant lastKnownSignal(Device device, Instant now) {
        Optional<Instant> fromRedis = lastSeen.lastSeen(device.getId());
        if (fromRedis.isPresent()) {
            return min(fromRedis.get(), now);
        }

        log.debug("Sin last_seen en Redis para {}; se consulta el historial", device.getId());
        Optional<Instant> fromDb = events
                .findLastDeviceEvent(device.getId(), now.minus(DB_LOOKBACK))
                .map(e -> e.getEventTime());
        if (fromDb.isPresent()) {
            return min(fromDb.get(), now);
        }

        // Nunca ha emitido: se cuenta desde que se dio de alta.
        return min(device.getCreatedAt(), now);
    }

    private Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    private boolean open(Device device, Instant reference, Duration silence, Instant now) {
        SilenceIncident incident = incidents.save(new SilenceIncident(
                device.getId(),
                device.getPatient().getId(),
                reference,
                device.getSilenceThresholdSeconds(),
                now));

        log.error("SILENCIO DETECTADO | paciente={} deviceId={} sin senal desde hace {}s "
                        + "(umbral {}s) incidencia={}",
                device.getPatient().getCode(), device.getId(), silence.getSeconds(),
                device.getSilenceThresholdSeconds(), incident.getId());

        inject(incident, device, now);
        return true;
    }

    /**
     * Inyecta el evento sintetico en el pipeline, con la misma clave de particion
     * y la misma cabecera de dispositivo que un evento real.
     * <p>
     * Publica directamente en Kafka, sin pasar por el webhook: si pasara por la
     * ingesta se anotaria el last_seen del dispositivo y el propio aviso de silencio
     * haria creer que el reloj volvio a hablar, cerrando la incidencia al instante.
     */
    private void inject(SilenceIncident incident, Device device, Instant now) {
        Instant at = now.truncatedTo(ChronoUnit.SECONDS);
        OsdEvent synthetic = SyntheticSilenceEvent.at(timeParser.toOsdTime(at));

        producer.publish(synthetic, device.getId(), device.getPatient().getCode());

        incident.setSyntheticDedupKey(
                DedupKeys.of(device.getId(), at, synthetic.alarmState()));
        incidents.save(incident);
    }

    private void close(SilenceIncident incident, Device device, Instant now) {
        incident.close(now);
        incidents.save(incident);
        log.warn("Senal recuperada | paciente={} deviceId={} la incidencia {} estuvo abierta {}s",
                device.getPatient().getCode(), device.getId(), incident.getId(),
                Duration.between(incident.getOpenedAt(), now).getSeconds());
    }
}
