package com.rmsolutions.centinela.simulator;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracion del simulador, enlazada desde 'app.simulator.*'.
 *
 * Vive en el modulo del simulador y no en AppProperties a proposito: el escenario
 * (NORMAL, SEIZURE, FALL...) es un concepto exclusivo de esta herramienta de prueba.
 * Tenerlo en la configuracion compartida obligaba a 'shared' a importar del
 * simulador, justo la dependencia invertida que el arquetipo prohibe.
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
@ConfigurationProperties(prefix = "app.simulator")
public record SimulatorProperties(
        String targetUrl,
        long intervalMs,
        int eventCount,
        Scenario scenario,
        String apiKey
) {
}
