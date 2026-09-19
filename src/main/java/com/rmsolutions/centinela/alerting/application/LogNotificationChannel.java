package com.rmsolutions.centinela.alerting.application;

import com.rmsolutions.centinela.ingestion.domain.OsdEvent;
import com.rmsolutions.centinela.shared.domain.Severity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Canal de sustitucion mientras no hay proveedores reales (Fase 3).
 *
 * <p>Deja constancia en el log de lo que se enviaria. No es un canal de verdad, pero
 * mantiene el flujo completo ejercitado de punta a punta: cuando lleguen voz, SMS
 * y push, se anaden al lado de este sin tocar nada mas.
 *
 * @author Roger Rojas
 * @since 2026-09-19
 */
@Slf4j
@Component
public class LogNotificationChannel implements NotificationChannel {

    @Override
    public void deliver(Severity severity, OsdEvent event) {
        switch (severity) {
            case CRITICAL -> log.error("[NOTIFY][EMERGENCIA] Se contactaria a los cuidadores AHORA "
                    + "(llamada + SMS + push). Evento={}", event);
            case WARNING -> log.warn("[NOTIFY][ADVERTENCIA] Aviso no urgente a cuidadores. "
                    + "Evento={}", event);
            case INFO -> log.debug("[NOTIFY][INFO] Telemetria, no se avisa. Evento={}", event);
        }
    }

    @Override
    public String name() {
        return "log";
    }
}
