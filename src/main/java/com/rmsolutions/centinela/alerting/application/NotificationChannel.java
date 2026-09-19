package com.rmsolutions.centinela.alerting.application;

import com.rmsolutions.centinela.ingestion.domain.OsdEvent;
import com.rmsolutions.centinela.shared.domain.Severity;

/**
 * Una via por la que avisar a los cuidadores.
 * <p>
 * Esta abstracción si se gana su sitio: la Fase 3 traera llamada de voz, SMS y
 * push, y cada una es una implementacion real con su proveedor y sus fallos.
 * Añadirlas no debe obligar a tocar el dispatcher ni el consumer de Kafka.
 *
 * @author Roger Rojas
 * @since 2026-09-19
 */
public interface NotificationChannel {

    void deliver(Severity severity, OsdEvent event);

    /**
     * Permite que un canal caro (una llamada) se reserve para lo critico y otro
     * barato (un push) atienda tambien las advertencias.
     */
    default boolean supports(Severity severity) {
        return true;
    }

    String name();
}
