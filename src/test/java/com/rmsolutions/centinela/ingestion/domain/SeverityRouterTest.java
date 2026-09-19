package com.rmsolutions.centinela.ingestion.domain;

import com.rmsolutions.centinela.shared.config.AppProperties;
import com.rmsolutions.centinela.shared.domain.Severity;
import com.rmsolutions.centinela.shared.kafka.Topics;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de la logica de ruteo. Es el componente mas critico:
 * una clasificacion erronea puede significar una emergencia no notificada.
 */
class SeverityRouterTest {

    private final SeverityRouter router =
            new SeverityRouter(new AppProperties(15, "America/Lima"));

    private OsdEvent event(int alarmState, Integer battery, Boolean connected) {
        return new OsdEvent("2026-09-18 00:05:32", alarmState, "X",
                1.0, 100.0, 200.0, 150.0, 80, battery, connected);
    }

    @Test
    void alarm_isCritical_andGoesToTheCriticalTopic() {
        RoutingDecision d = router.route(event(2, 90, true));
        assertThat(d.severity()).isEqualTo(Severity.CRITICAL);
        assertThat(d.topics()).contains(Topics.RAW, Topics.CRITICAL);
    }

    @Test
    void fall_isCritical() {
        assertThat(router.route(event(3, 90, true)).severity()).isEqualTo(Severity.CRITICAL);
    }

    @Test
    void okState_isInfo_andGoesToTelemetry() {
        RoutingDecision d = router.route(event(0, 90, true));
        assertThat(d.severity()).isEqualTo(Severity.INFO);
        assertThat(d.topics()).contains(Topics.RAW, Topics.TELEMETRY);
    }

    @Test
    void disconnectedWatch_escalatesToWarning_evenWhenStateIsOk() {
        RoutingDecision d = router.route(event(0, 90, false));
        assertThat(d.severity()).isEqualTo(Severity.WARNING);
        assertThat(d.watchDisconnected()).isTrue();
        assertThat(d.topics()).contains(Topics.WARNING);
    }

    @Test
    void lowBattery_escalatesToWarning() {
        RoutingDecision d = router.route(event(0, 10, true));
        assertThat(d.severity()).isEqualTo(Severity.WARNING);
        assertThat(d.lowBattery()).isTrue();
    }

    @Test
    void unknownState_escalatesToWarning_byFailSafe() {
        RoutingDecision d = router.route(event(99, 90, true));
        assertThat(d.severity()).isEqualTo(Severity.WARNING);
    }
}
