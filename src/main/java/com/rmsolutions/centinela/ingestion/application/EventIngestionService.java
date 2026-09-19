package com.rmsolutions.centinela.ingestion.application;

import com.rmsolutions.centinela.ingestion.domain.OsdEvent;
import com.rmsolutions.centinela.ingestion.domain.RoutingDecision;
import com.rmsolutions.centinela.ingestion.messaging.EventRoutingProducer;
import com.rmsolutions.centinela.ingestion.redis.DeviceRateLimiter;
import com.rmsolutions.centinela.ingestion.redis.EdgeDeduplicator;
import com.rmsolutions.centinela.registry.application.DeviceRegistryService;
import com.rmsolutions.centinela.registry.application.AuthenticatedDevice;
import com.rmsolutions.centinela.shared.domain.DedupKeys;
import com.rmsolutions.centinela.shared.domain.OsdTimeParser;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Caso de uso de la ingesta: autentica el dispositivo, aplica los guardias de
 * borde y publica el evento en el pipeline.
 *
 * El orden de los pasos es deliberado:
 *
 *  1. AUTENTICAR primero. Ni siquiera se cuenta trafico de quien no se identifica,
 *     para que un desconocido no pueda consumir la cuota de un dispositivo real.
 *  2. LIMITAR despues. Protege al sistema de un bucle o un abuso.
 *  3. DEDUPLICAR al final, ya con la identidad del dispositivo, que es lo que
 *     hace unica la clave.
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
@Slf4j
@Service
@Profile("!simulator")
@RequiredArgsConstructor
public class EventIngestionService {

    private final DeviceRegistryService registry;
    private final DeviceRateLimiter rateLimiter;
    private final EdgeDeduplicator deduplicator;
    private final EventRoutingProducer producer;
    private final OsdTimeParser timeParser;

    public IngestOutcome ingest(String apiKey, OsdEvent event) {
        Optional<AuthenticatedDevice> device = registry.verify(apiKey);
        if (device.isEmpty()) {
            // No se registra la clave presentada, ni siquiera fallida.
            log.warn("Webhook rechazado: credencial de dispositivo ausente, desconocida o revocada");
            return new IngestOutcome.Unauthorized();
        }

        AuthenticatedDevice sender = device.get();

        if (!rateLimiter.allow(sender.deviceId())) {
            return new IngestOutcome.RateLimited();
        }

        Optional<Instant> eventTime = timeParser.toInstant(event.time());
        if (eventTime.isEmpty()) {
            // Sin instante no hay clave de deduplicacion posible, y guardar el evento
            // con una fecha inventada corromperia el historial.
            log.error("Webhook rechazado | deviceId={} el campo 'Time' no es interpretable: '{}'",
                    sender.deviceId(), event.time());
            return new IngestOutcome.Unprocessable("el campo 'Time' no es interpretable");
        }

        String dedupKey = DedupKeys.of(sender.deviceId(), eventTime.get(), event.alarmState());

        if (!deduplicator.markAsSeen(dedupKey)) {
            log.debug("Reenvio descartado en el borde | deviceId={} dedupKey={}",
                    sender.deviceId(), dedupKey);
            return new IngestOutcome.Duplicate(dedupKey);
        }

        RoutingDecision decision = producer.publish(event, sender.deviceId(), sender.patientCode());
        return new IngestOutcome.Accepted(decision.severity(), decision.topics(), dedupKey);
    }
}
