package com.rmsolutions.centinela.shared.kafka;

import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

/**
 * Factory de listeners que entrega el mensaje como TEXTO, sin deserializar.
 *
 * <p>El consumidor de persistencia lo necesita porque guarda el payload original en
 * la columna 'raw_payload'. Si recibiera un OsdEvent ya deserializado, re-serializarlo
 * perderia cualquier campo que OSD haya anadido en una version nueva: el record
 * declara @JsonIgnoreProperties(ignoreUnknown = true), asi que esos campos se
 * descartan al leer. Guardar esa re-serializacion seria llamar "crudo" a algo que
 * ya paso por un filtro, justo lo contrario de lo que la columna promete.
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
@Configuration
@Profile("!simulator")
public class KafkaRawJsonConfig {

    public static final String FACTORY = "rawJsonListenerFactory";

    @Bean(FACTORY)
    public ConcurrentKafkaListenerContainerFactory<String, String> rawJsonListenerFactory(
            KafkaProperties properties) {

        Map<String, Object> props = properties.buildConsumerProperties();
        // Solo cambiamos el deserializador de valor; el resto de la configuracion
        // (bootstrap servers, auto-offset-reset, etc.) se hereda de application.yml.
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(props));
        // Mismo criterio que el resto del pipeline: confirmamos offset por registro.
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }
}
