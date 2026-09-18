package com.centinela.routing;

import com.centinela.config.AppProperties;
import com.centinela.config.Topics;
import com.centinela.domain.OsdEvent;
import com.centinela.domain.Severity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de la logica de ruteo. Es el componente mas critico:
 * una clasificacion erronea puede significar una emergencia no notificada.
 */
class SeverityRouterTest {

    private final SeverityRouter router =
            new SeverityRouter(new AppProperties("child-001", "token", 15, null));

    private OsdEvent event(int alarmState, Integer battery, Boolean connected) {
        return new OsdEvent("2026-09-18 00:05:32", alarmState, "X",
                1.0, 100.0, 200.0, 150.0, 80, battery, connected);
    }

    @Test
    void alarma_esCritica_yVaAlTopicoCritico() {
        RoutingDecision d = router.route(event(2, 90, true));
        assertThat(d.severity()).isEqualTo(Severity.CRITICAL);
        assertThat(d.topics()).contains(Topics.RAW, Topics.CRITICAL);
    }

    @Test
    void caida_esCritica() {
        assertThat(router.route(event(3, 90, true)).severity()).isEqualTo(Severity.CRITICAL);
    }

    @Test
    void estadoOk_esInfo_yVaATelemetria() {
        RoutingDecision d = router.route(event(0, 90, true));
        assertThat(d.severity()).isEqualTo(Severity.INFO);
        assertThat(d.topics()).contains(Topics.RAW, Topics.TELEMETRY);
    }

    @Test
    void relojDesconectado_escalaAWarning_aunqueEstadoSeaOk() {
        RoutingDecision d = router.route(event(0, 90, false));
        assertThat(d.severity()).isEqualTo(Severity.WARNING);
        assertThat(d.watchDisconnected()).isTrue();
        assertThat(d.topics()).contains(Topics.WARNING);
    }

    @Test
    void bateriaBaja_escalaAWarning() {
        RoutingDecision d = router.route(event(0, 10, true));
        assertThat(d.severity()).isEqualTo(Severity.WARNING);
        assertThat(d.lowBattery()).isTrue();
    }

    @Test
    void estadoDesconocido_escalaAWarning_porFailSafe() {
        RoutingDecision d = router.route(event(99, 90, true));
        assertThat(d.severity()).isEqualTo(Severity.WARNING);
    }
}
