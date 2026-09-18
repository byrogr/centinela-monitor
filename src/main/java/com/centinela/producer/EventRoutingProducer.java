package com.centinela.producer;

import com.centinela.config.AppProperties;
import com.centinela.domain.OsdEvent;
import com.centinela.routing.RoutingDecision;
import com.centinela.routing.SeverityRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class EventRoutingProducer {

    private static final Logger log = LoggerFactory.getLogger(EventRoutingProducer.class);

    private final KafkaTemplate<String, OsdEvent> kafkaTemplate;
    private final SeverityRouter router;
    private final AppProperties props;

    public EventRoutingProducer(KafkaTemplate<String, OsdEvent> kafkaTemplate,
                                SeverityRouter router,
                                AppProperties props) {
        this.kafkaTemplate = kafkaTemplate;
        this.router = router;
        this.props = props;
    }

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
