package com.rmsolutions.centinela.history.messaging;

import com.rmsolutions.centinela.history.application.EventPersistenceService;
import com.rmsolutions.centinela.shared.kafka.EventHeaders;
import com.rmsolutions.centinela.shared.kafka.KafkaRawJsonConfig;
import com.rmsolutions.centinela.shared.kafka.Topics;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Adaptador de entrada: traduce un mensaje de 'osd.events.raw' en una llamada al
 * caso de uso de persistencia. No contiene logica de negocio a proposito.
 *
 * <p>Corre en su PROPIO consumer group, independiente de los de alerta: si la
 * persistencia se degrada, las notificaciones criticas siguen saliendo por
 * 'osd.alerts.critical'. Guardar el historial y avisar a un cuidador son dos
 * responsabilidades que no deben poder tumbarse la una a la otra.
 *
 * <p>Recibe el mensaje como TEXTO (ver {@link KafkaRawJsonConfig}) para que el
 * payload original llegue intacto a la columna raw_payload.
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
@Component
@Profile("!simulator")
@RequiredArgsConstructor
public class EventPersistenceConsumer {

    public static final String GROUP = "centinela-persistence";

    private final EventPersistenceService persistence;

    @KafkaListener(
            topics = Topics.RAW,
            groupId = GROUP,
            containerFactory = KafkaRawJsonConfig.FACTORY)
    public void onEvent(@Payload String rawJson,
                        @Header(KafkaHeaders.RECEIVED_KEY) String patientCode,
                        @Header(name = EventHeaders.DEVICE_ID, required = false) String deviceId) {
        persistence.persist(rawJson, patientCode, deviceId);
    }
}
