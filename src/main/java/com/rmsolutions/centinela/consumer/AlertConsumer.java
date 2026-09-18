package com.rmsolutions.centinela.consumer;

import com.rmsolutions.centinela.config.Topics;
import com.rmsolutions.centinela.domain.OsdEvent;
import com.rmsolutions.centinela.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class AlertConsumer {

    private static final Logger log = LoggerFactory.getLogger(AlertConsumer.class);

    private final NotificationService notifications;

    public AlertConsumer(NotificationService notifications) {
        this.notifications = notifications;
    }

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
