package com.centinela.config;

import com.centinela.simulator.Scenario;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de negocio, enlazadas desde 'app.*' en application.yml.
 * Binding por constructor (record), con relaxed binding:
 *   child-id -> childId, low-battery-threshold -> lowBatteryThreshold, etc.
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String childId,
        String webhookToken,
        int lowBatteryThreshold,
        Simulator simulator
) {
    public record Simulator(
            String targetUrl,
            long intervalMs,
            int eventCount,
            Scenario scenario
    ) {
    }
}
