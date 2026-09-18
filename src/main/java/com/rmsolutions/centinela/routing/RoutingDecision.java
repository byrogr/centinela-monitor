package com.rmsolutions.centinela.routing;

import com.rmsolutions.centinela.domain.AlarmState;
import com.rmsolutions.centinela.domain.Severity;

import java.util.List;

/**
 * Resultado de evaluar un evento: que severidad tiene y a que topicos va.
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
public record RoutingDecision(
        AlarmState alarmState,
        Severity severity,
        boolean watchDisconnected,
        boolean lowBattery,
        List<String> topics
) {
}
