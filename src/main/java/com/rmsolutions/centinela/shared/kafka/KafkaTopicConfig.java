package com.rmsolutions.centinela.shared.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declara los topicos para que Spring los cree al arrancar (dev/local).
 * replicas(1) es solo para local. En Azure Event Hubs / cluster real,
 * el factor de replicacion lo gestiona la plataforma (>=3).
 *
 * No aplica en perfil "simulator" (ese no habla con Kafka).
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
@Configuration
@Profile("!simulator")
public class KafkaTopicConfig {

    @Bean
    public NewTopic rawTopic() {
        return TopicBuilder.name(Topics.RAW).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic telemetryTopic() {
        return TopicBuilder.name(Topics.TELEMETRY).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic warningTopic() {
        return TopicBuilder.name(Topics.WARNING).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic criticalTopic() {
        return TopicBuilder.name(Topics.CRITICAL).partitions(3).replicas(1).build();
    }
}
