package com.rmsolutions.centinela.ingestion.messaging;

import com.rmsolutions.centinela.ingestion.domain.OsdEvent;
import com.rmsolutions.centinela.shared.kafka.EventHeaders;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import com.rmsolutions.centinela.ingestion.domain.RoutingDecision;
import com.rmsolutions.centinela.ingestion.domain.SeverityRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Productor de Kafka. Recibe un evento, consulta al SeverityRouter y publica
 * el mensaje en los topicos correspondientes.
 *
 * La CLAVE del mensaje es el code del paciente: garantiza que todos los eventos
 * de un mismo nino caigan en la misma particion y se procesen EN ORDEN.
 *
 * El dispositivo emisor viaja en una cabecera. Antes el consumidor de persistencia
 * tenia que deducirlo del paciente, algo que no tiene solucion cuando hay mas de
 * un dispositivo activo; ahora el webhook, que ya lo autentico, lo propaga.
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

    /**
     * @param patientCode clave de particion; debe ser el code real del paciente
     *                    emisor, no un valor fijo de configuracion.
     * @param deviceId    dispositivo ya autenticado por el webhook.
     */
    public RoutingDecision publish(OsdEvent event, UUID deviceId, String patientCode) {
        RoutingDecision decision = router.route(event);

        for (String topic : decision.topics()) {
            ProducerRecord<String, OsdEvent> record =
                    new ProducerRecord<>(topic, null, patientCode, event);
            record.headers().add(EventHeaders.DEVICE_ID,
                    deviceId.toString().getBytes(StandardCharsets.UTF_8));

            kafkaTemplate.send(record).whenComplete((result, ex) -> {
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

        log.info("Evento ruteado | paciente={} deviceId={} estado={} severidad={} topics={} "
                        + "bateria={}% conectado={}",
                patientCode, deviceId, decision.alarmState(), decision.severity(),
                decision.topics(), event.batteryLevel(), event.watchConnected());

        return decision;
    }
}
