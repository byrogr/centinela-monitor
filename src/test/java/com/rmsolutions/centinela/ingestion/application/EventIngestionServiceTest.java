package com.rmsolutions.centinela.ingestion.application;

import com.rmsolutions.centinela.ingestion.domain.OsdEvent;
import com.rmsolutions.centinela.ingestion.domain.RoutingDecision;
import com.rmsolutions.centinela.ingestion.messaging.EventRoutingProducer;
import com.rmsolutions.centinela.ingestion.redis.DeviceRateLimiter;
import com.rmsolutions.centinela.ingestion.redis.EdgeDeduplicator;
import com.rmsolutions.centinela.registry.application.DeviceRegistryService;
import com.rmsolutions.centinela.registry.application.AuthenticatedDevice;
import com.rmsolutions.centinela.shared.config.AppProperties;
import com.rmsolutions.centinela.ingestion.domain.AlarmState;
import com.rmsolutions.centinela.shared.domain.DedupKeys;
import com.rmsolutions.centinela.shared.domain.OsdTimeParser;
import com.rmsolutions.centinela.shared.domain.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas de la decision de ingesta.
 *
 * Lo que mas importa aqui no son los caminos felices, sino que cada guardia falle
 * hacia el lado correcto: la autenticacion CERRADA y los guardias de Redis ABIERTOS.
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
class EventIngestionServiceTest {

    private static final String VALID_KEY = "lenzo_clave-de-prueba";
    private static final UUID DEVICE_ID = UUID.randomUUID();

    private DeviceRegistryService registry;
    private DeviceRateLimiter rateLimiter;
    private EdgeDeduplicator deduplicator;
    private EventRoutingProducer producer;
    private EventIngestionService service;

    @BeforeEach
    void setUp() {
        registry = mock(DeviceRegistryService.class);
        rateLimiter = mock(DeviceRateLimiter.class);
        deduplicator = mock(EdgeDeduplicator.class);
        producer = mock(EventRoutingProducer.class);

        AuthenticatedDevice device =
                new AuthenticatedDevice(DEVICE_ID, UUID.randomUUID(), "child-001", 240);
        when(registry.verify(anyString())).thenReturn(Optional.empty());
        when(registry.verify(VALID_KEY)).thenReturn(Optional.of(device));

        when(rateLimiter.allow(any())).thenReturn(true);
        when(deduplicator.markAsSeen(anyString())).thenReturn(true);
        when(producer.publish(any(), any(), anyString())).thenReturn(new RoutingDecision(
                AlarmState.ALARM, Severity.CRITICAL, false, false,
                List.of("osd.events.raw", "osd.alerts.critical")));

        AppProperties props = new AppProperties(15, "America/Lima");
        service = new EventIngestionService(
                registry, rateLimiter, deduplicator, producer, new OsdTimeParser(props));
    }

    private OsdEvent event(String time) {
        return new OsdEvent(time, 2, "ALARM", 5.4, 1250.3, 4500.1, 3800.8, 134, 88, true);
    }

    @Test
    void aValidKeyPublishesTheEvent() {
        IngestOutcome outcome = service.ingest(VALID_KEY, event("2026-09-18 00:05:32"));

        assertThat(outcome).isInstanceOf(IngestOutcome.Accepted.class);
        verify(producer).publish(any(), any(), anyString());
    }

    @Test
    void theDedupKeyIsBuiltFromTheAuthenticatedDevice() {
        IngestOutcome outcome = service.ingest(VALID_KEY, event("2026-09-18 00:05:32"));

        String expected = DedupKeys.of(DEVICE_ID, Instant.parse("2026-09-18T05:05:32Z"), 2);
        assertThat(outcome)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories
                        .type(IngestOutcome.Accepted.class))
                .extracting(IngestOutcome.Accepted::dedupKey)
                .as("debe coincidir con la que calculara el consumidor de persistencia")
                .isEqualTo(expected);
    }

    @Test
    void anUnknownKeyIsRejectedAndNothingIsPublished() {
        IngestOutcome outcome = service.ingest("clave-que-no-existe", event("2026-09-18 00:05:32"));

        assertThat(outcome).isInstanceOf(IngestOutcome.Unauthorized.class);
        verify(producer, never()).publish(any(), any(), anyString());
    }

    @Test
    void anUnauthenticatedRequestDoesNotEvenConsumeQuota() {
        service.ingest("clave-que-no-existe", event("2026-09-18 00:05:32"));

        verify(rateLimiter, never()).allow(any());
    }

    @Test
    void anEventOverTheQuotaIsRejected() {
        when(rateLimiter.allow(DEVICE_ID)).thenReturn(false);

        assertThat(service.ingest(VALID_KEY, event("2026-09-18 00:05:32")))
                .isInstanceOf(IngestOutcome.RateLimited.class);
        verify(producer, never()).publish(any(), any(), anyString());
    }

    @Test
    void aResentEventIsDiscardedAtTheEdge() {
        when(deduplicator.markAsSeen(anyString())).thenReturn(false);

        assertThat(service.ingest(VALID_KEY, event("2026-09-18 00:05:32")))
                .isInstanceOf(IngestOutcome.Duplicate.class);
        verify(producer, never()).publish(any(), any(), anyString());
    }

    @Test
    void anUnreadableTimeIsRejectedInsteadOfInventingADate() {
        assertThat(service.ingest(VALID_KEY, event("no es una fecha")))
                .isInstanceOf(IngestOutcome.Unprocessable.class);
        verify(producer, never()).publish(any(), any(), anyString());
    }

    @Test
    void theEventStillFlowsWhenTheRedisGuardsFailOpen() {
        // Los guardias devuelven true cuando Redis no responde (ver sus clases).
        // Esta prueba fija el contrato: una caida de Redis NO puede dejar al nino
        // sin monitorizacion.
        when(rateLimiter.allow(DEVICE_ID)).thenReturn(true);
        when(deduplicator.markAsSeen(anyString())).thenReturn(true);

        assertThat(service.ingest(VALID_KEY, event("2026-09-18 00:05:32")))
                .isInstanceOf(IngestOutcome.Accepted.class);
        verify(producer).publish(any(), any(), anyString());
    }
}
