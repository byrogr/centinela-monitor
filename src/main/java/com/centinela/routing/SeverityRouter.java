package com.centinela.routing;

import com.centinela.config.AppProperties;
import com.centinela.config.Topics;
import com.centinela.domain.AlarmState;
import com.centinela.domain.OsdEvent;
import com.centinela.domain.Severity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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
public class SeverityRouter {

    private final AppProperties props;

    public SeverityRouter(AppProperties props) {
        this.props = props;
    }

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
