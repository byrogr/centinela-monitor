package com.rmsolutions.centinela.producer;

import com.rmsolutions.centinela.config.AppProperties;
import com.rmsolutions.centinela.domain.OsdEvent;
import com.rmsolutions.centinela.routing.RoutingDecision;
import com.rmsolutions.centinela.routing.SeverityRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Productor de Kafka. Recibe un evento, consulta al SeverityRouter y publica
 * el mensaje en los topicos correspondientes.
 *
 * La CLAVE del mensaje es el childId: garantiza que todos los eventos de un
 * mismo nino caigan en la misma particion y se procesen EN ORDEN.
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
@Component
@Profile("!simulator")
@Slf4j
@RequiredArgsConstructor
public class EventRoutingProducer {

    private final KafkaTemplate<String, OsdEvent> kafkaTemplate;
    private final SeverityRouter router;
    private final AppProperties props;

    public RoutingDecision publish(OsdEvent event) {
        RoutingDecision decision = router.route(event);
        String key = props.childId();

        for (String topic : decision.topics()) {
            kafkaTemplate.send(topic, key, event).whenComplete((result, ex) -> {
                if (ex != null) {
                    // En un sistema critico, un fallo de publicacion se ESCALA,
                    // no se silencia. Aqui minimamente lo registramos a nivel ERROR.
                    log.error("Fallo publicando en topico '{}': {}", topic, ex.getMessage(), ex);
                } else {
                    log.debug("Publicado en '{}' particion={} offset={}",
                            topic,
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                }
            });
        }

        log.info("Evento ruteado | childId={} estado={} severidad={} topics={} bateria={}% conectado={}",
                key, decision.alarmState(), decision.severity(), decision.topics(),
                event.batteryLevel(), event.watchConnected());

        return decision;
    }
}
