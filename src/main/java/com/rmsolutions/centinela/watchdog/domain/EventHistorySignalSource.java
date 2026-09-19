package com.rmsolutions.centinela.watchdog.domain;

import com.rmsolutions.centinela.history.domain.Event;
import com.rmsolutions.centinela.history.persistence.EventRepository;
import com.rmsolutions.centinela.registry.domain.Device;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Respaldo: el historial de eventos.
 *
 * <p>Existe porque Redis es cache y puede estar caido o frio. Sin esta fuente, una
 * caida de Redis dejaria al vigilante sin criterio justo cuando mas falta hace.
 *
 * @author Roger Rojas
 * @since 2026-09-19
 */
@Component
@Profile("!simulator")
@Order(2)
@RequiredArgsConstructor
public class EventHistorySignalSource implements SignalSource {

    /** Mas atras no merece la pena mirar: cualquier cosa asi ya es silencio. */
    private static final Duration LOOKBACK = Duration.ofDays(2);

    private final EventRepository events;

    @Override
    public Optional<Instant> lastSignal(Device device, Instant now) {
        return events.findLastDeviceEvent(device.getId(), now.minus(LOOKBACK))
                .map(Event::getEventTime);
    }

    @Override
    public String description() {
        return "ultimo evento en el historial";
    }
}
