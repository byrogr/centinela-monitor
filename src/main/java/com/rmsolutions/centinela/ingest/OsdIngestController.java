package com.rmsolutions.centinela.ingest;

import com.rmsolutions.centinela.config.AppProperties;
import com.rmsolutions.centinela.domain.OsdEvent;
import com.rmsolutions.centinela.producer.EventRoutingProducer;
import com.rmsolutions.centinela.routing.RoutingDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Webhook HTTP. El celular con OSD hace POST del JSON aqui.
 *
 * Buenas practicas aplicadas:
 *  - Autenticacion por secreto compartido (cabecera X-OSD-Token). Un endpoint
 *    publico que dispara emergencias no puede quedar abierto a Internet.
 *  - Respuesta 202 Accepted: confirmamos rapido y el procesamiento (Kafka)
 *    continua de forma asincrona. Un webhook debe responder pronto.
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
@RestController
@RequestMapping("/api/v1/osd")
@Profile("!simulator")
public class OsdIngestController {

    private static final Logger log = LoggerFactory.getLogger(OsdIngestController.class);

    private final EventRoutingProducer producer;
    private final AppProperties props;

    public OsdIngestController(EventRoutingProducer producer, AppProperties props) {
        this.producer = producer;
        this.props = props;
    }

    @PostMapping("/events")
    public ResponseEntity<Map<String, Object>> ingest(
            @RequestHeader(value = "X-OSD-Token", required = false) String token,
            @RequestBody OsdEvent event) {

        if (!tokenValido(token)) {
            log.warn("Webhook rechazado: token invalido o ausente");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (event == null) {
            return ResponseEntity.badRequest().build();
        }

        RoutingDecision decision = producer.publish(event);

        return ResponseEntity.accepted().body(Map.of(
                "status", "accepted",
                "severity", decision.severity().name(),
                "routedTo", decision.topics()
        ));
    }

    private boolean tokenValido(String token) {
        String expected = props.webhookToken();
        // Si no se configuro token, no bloqueamos (util en dev). En prod SIEMPRE debe existir.
        if (expected == null || expected.isBlank()) {
            return true;
        }
        return expected.equals(token);
    }
}
