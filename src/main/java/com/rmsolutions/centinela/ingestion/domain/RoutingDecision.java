package com.rmsolutions.centinela.ingestion.domain;

import com.rmsolutions.centinela.ingestion.domain.AlarmState;
import com.rmsolutions.centinela.shared.domain.Severity;
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
