package com.rmsolutions.centinela.alerting.messaging;

import com.rmsolutions.centinela.alerting.application.NotificationDispatcher;
import com.rmsolutions.centinela.ingestion.domain.OsdEvent;
import com.rmsolutions.centinela.shared.domain.Severity;
import com.rmsolutions.centinela.shared.kafka.Topics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consume los topicos de alerta y pide que se avise a los cuidadores.
 * <p>
 * Cada topico tiene su PROPIO groupId a proposito: un atasco procesando
 * advertencias no puede retrasar una emergencia. Son dos listeners separados por
 * eso, no por tener logica distinta; el camino de codigo es el mismo.
 *
 * @author Roger Rojas
 * @since 2026-09-19
 */
@Slf4j
@Component
@Profile("!simulator")
@RequiredArgsConstructor
public class AlertConsumer {

    private final NotificationDispatcher notifications;

    @KafkaListener(topics = Topics.CRITICAL, groupId = "centinela-critical")
    public void onCritical(OsdEvent event) {
        log.error("ALERTA CRITICA | estado={} frase='{}' hr={} time={}",
                event.alarmState(), event.alarmPhrase(), event.heartRate(), event.time());
        notifications.dispatch(Severity.CRITICAL, event);
    }

    @KafkaListener(topics = Topics.WARNING, groupId = "centinela-warning")
    public void onWarning(OsdEvent event) {
        log.warn("Advertencia | estado={} bateria={}% conectado={} time={}",
                event.alarmState(), event.batteryLevel(), event.watchConnected(), event.time());
        notifications.dispatch(Severity.WARNING, event);
    }
}
