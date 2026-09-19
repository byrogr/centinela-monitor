package com.rmsolutions.centinela.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de negocio transversales, enlazadas desde 'app.*' en application.yml.
 * La configuracion propia de un modulo vive en el modulo (ver SimulatorProperties).
 * Binding por constructor (record), con relaxed binding:
 *   child-id -> childId, low-battery-threshold -> lowBatteryThreshold, etc.
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        int lowBatteryThreshold,
        String osdTimeZone
) {
}
