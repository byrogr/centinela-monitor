package com.rmsolutions.centinela.consumer;

import com.rmsolutions.centinela.config.Topics;
import com.rmsolutions.centinela.domain.OsdEvent;
import com.rmsolutions.centinela.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumidores de alertas.
 *
 * Cada topico tiene su propio groupId, de modo que critico y advertencia
 * escalan de forma independiente y con su propio ritmo de consumo.
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
@Component
@Profile("!simulator")
@Slf4j
@RequiredArgsConstructor
public class AlertConsumer {

    private final NotificationService notifications;

    @KafkaListener(topics = Topics.CRITICAL, groupId = "centinela-critical")
    public void onCritical(OsdEvent event) {
        log.error("ALERTA CRITICA | estado={} frase='{}' hr={} time={}",
                event.alarmState(), event.alarmPhrase(), event.heartRate(), event.time());
        notifications.dispatchEmergency(event);
    }

    @KafkaListener(topics = Topics.WARNING, groupId = "centinela-warning")
    public void onWarning(OsdEvent event) {
        log.warn("Advertencia | estado={} bateria={}% conectado={} time={}",
                event.alarmState(), event.batteryLevel(), event.watchConnected(), event.time());
        notifications.dispatchWarning(event);
    }
}
