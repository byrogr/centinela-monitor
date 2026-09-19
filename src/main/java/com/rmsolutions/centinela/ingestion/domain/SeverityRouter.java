package com.rmsolutions.centinela.ingestion.domain;

import com.rmsolutions.centinela.ingestion.domain.AlarmState;
import com.rmsolutions.centinela.ingestion.domain.OsdEvent;
import com.rmsolutions.centinela.shared.config.AppProperties;
import com.rmsolutions.centinela.shared.domain.Severity;
import com.rmsolutions.centinela.shared.kafka.Topics;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Traduce un evento OSD en una decision de ruteo.
 *
 * Filosofia fail-safe: ante duda o degradacion (estado desconocido, reloj
 * desconectado, bateria baja) NUNCA se ignora; se escala al menos a WARNING.
 * Perder telemetria del reloj del nino es en si mismo motivo de aviso.
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
@Component
@RequiredArgsConstructor
public class SeverityRouter {

    private final AppProperties props;

    public RoutingDecision route(OsdEvent event) {
        AlarmState state = AlarmState.fromCode(event.alarmState());

        Severity severity = switch (state) {
            case ALARM, FALL, MANUAL_ALARM -> Severity.CRITICAL;
            case WARNING, UNKNOWN -> Severity.WARNING; // UNKNOWN escala por seguridad
            case OK, MUTE -> Severity.INFO;
        };

        boolean disconnected = Boolean.FALSE.equals(event.watchConnected());
        boolean lowBattery = event.batteryLevel() != null
                && event.batteryLevel() <= props.lowBatteryThreshold();

        // RAW siempre: traza completa e inmutable de todo lo que ocurre.
        List<String> topics = new ArrayList<>();
        topics.add(Topics.RAW);

        if (severity == Severity.CRITICAL) {
            topics.add(Topics.CRITICAL);
        } else if (severity == Severity.WARNING || disconnected || lowBattery) {
            severity = Severity.WARNING; // escala condiciones de salud/conectividad
            topics.add(Topics.WARNING);
        } else {
            topics.add(Topics.TELEMETRY);
        }

        return new RoutingDecision(state, severity, disconnected, lowBattery, topics);
    }
}
