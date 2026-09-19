package com.rmsolutions.centinela.watchdog.domain;

import com.rmsolutions.centinela.registry.domain.Device;
import com.rmsolutions.centinela.shared.redis.LastSeenStore;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * Fuente principal: la marca que la ingesta anota en Redis al recibir cada evento.
 * Es la mas fresca, porque no espera a que el evento recorra el pipeline.
 *
 * @author Roger Rojas
 * @since 2026-09-19
 */
@Component
@Profile("!simulator")
@Order(1)
@RequiredArgsConstructor
public class LastSeenSignalSource implements SignalSource {

    private final LastSeenStore lastSeen;

    @Override
    public Optional<Instant> lastSignal(Device device, Instant now) {
        return lastSeen.lastSeen(device.getId());
    }

    @Override
    public String description() {
        return "last_seen en Redis";
    }
}
