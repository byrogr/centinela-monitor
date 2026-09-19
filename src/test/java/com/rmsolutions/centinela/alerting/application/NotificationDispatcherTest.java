package com.rmsolutions.centinela.alerting.application;

import com.rmsolutions.centinela.ingestion.domain.OsdEvent;
import com.rmsolutions.centinela.shared.domain.Severity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Pruebas del reparto de avisos entre canales.
 *
 * @author Roger Rojas
 * @since 2026-09-19
 */
class NotificationDispatcherTest {

    private static final OsdEvent EVENT =
            new OsdEvent("2026-09-19 00:05:32", 2, "ALARM", 5.4, 1250.3, 4500.1, 3800.8, 134, 88, true);

    /** Canal de prueba que apunta lo que recibe, o revienta si se le pide. */
    private static class SpyChannel implements NotificationChannel {
        private final String name;
        private final boolean fails;
        private final Severity only;
        final List<Severity> delivered = new ArrayList<>();

        SpyChannel(String name, boolean fails, Severity only) {
            this.name = name;
            this.fails = fails;
            this.only = only;
        }

        @Override
        public void deliver(Severity severity, OsdEvent event) {
            if (fails) {
                throw new IllegalStateException("proveedor caido");
            }
            delivered.add(severity);
        }

        @Override
        public boolean supports(Severity severity) {
            return only == null || only == severity;
        }

        @Override
        public String name() {
            return name;
        }
    }

    @Test
    void everyChannelThatSupportsTheSeverityGetsTheNotice() {
        SpyChannel sms = new SpyChannel("sms", false, null);
        SpyChannel push = new SpyChannel("push", false, null);

        new NotificationDispatcher(List.of(sms, push)).dispatch(Severity.CRITICAL, EVENT);

        assertThat(sms.delivered).containsExactly(Severity.CRITICAL);
        assertThat(push.delivered).containsExactly(Severity.CRITICAL);
    }

    @Test
    void aBrokenChannelDoesNotStopTheOthers() {
        // Es el caso que justifica el try por canal: si el proveedor de SMS esta
        // caido, la llamada de voz y el push tienen que salir igual. Propagar la
        // excepcion dejaria al cuidador sin NINGUN aviso por culpa de uno solo.
        SpyChannel broken = new SpyChannel("sms", true, null);
        SpyChannel working = new SpyChannel("voz", false, null);

        NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(broken, working));

        assertThatCode(() -> dispatcher.dispatch(Severity.CRITICAL, EVENT))
                .doesNotThrowAnyException();
        assertThat(working.delivered)
                .as("el canal sano debe haber avisado pese al fallo del anterior")
                .containsExactly(Severity.CRITICAL);
    }

    @Test
    void anExpensiveChannelCanReserveItselfForCriticalEvents() {
        SpyChannel voice = new SpyChannel("voz", false, Severity.CRITICAL);
        SpyChannel push = new SpyChannel("push", false, null);

        NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(voice, push));
        dispatcher.dispatch(Severity.WARNING, EVENT);

        assertThat(voice.delivered).as("una llamada no se hace por una bateria baja").isEmpty();
        assertThat(push.delivered).containsExactly(Severity.WARNING);
    }

    @Test
    void whenEveryChannelFailsTheDispatcherStillDoesNotThrow() {
        NotificationDispatcher dispatcher = new NotificationDispatcher(
                List.of(new SpyChannel("sms", true, null), new SpyChannel("voz", true, null)));

        assertThatCode(() -> dispatcher.dispatch(Severity.CRITICAL, EVENT))
                .as("tumbar el consumidor de Kafka no avisaria a nadie tampoco")
                .doesNotThrowAnyException();
    }

    @Test
    void withNoChannelsAtAllNothingBreaks() {
        assertThatCode(() -> new NotificationDispatcher(List.of()).dispatch(Severity.CRITICAL, EVENT))
                .doesNotThrowAnyException();
    }
}
