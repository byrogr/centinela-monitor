package com.rmsolutions.centinela.simulator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El perfil 'simulator' no levanta Kafka, ni JPA, ni Redis: es un cliente HTTP
 * a secas. Eso significa que cualquier bean nuevo que dependa de un repositorio
 * o de un KafkaTemplate y olvide su @Profile("!simulator") rompe el simulador
 * SIN romper la compilacion ni ningun otro test.
 *
 * Esta prueba es ese guard: levanta el contexto del simulador y nada mas.
 * No necesita contenedores, justamente porque ese perfil no toca infraestructura.
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
@SpringBootTest
@ActiveProfiles("simulator")
// Con 0 eventos el bucle del simulador no se ejecuta: el test no hace peticiones HTTP.
@TestPropertySource(properties = "app.simulator.event-count=0")
class SimulatorProfileTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void theSimulatorContextStartsWithoutInfrastructure() {
        assertThat(context.getBean(OsdSimulator.class)).isNotNull();
        assertThat(context.getBean(ScenarioGenerator.class)).isNotNull();
    }

    @Test
    void theSimulatorDragsNoBeanNeedingDatabaseOrKafka() {
        assertThat(context.getBeanNamesForType(javax.sql.DataSource.class))
                .as("el perfil simulador excluye JPA a proposito")
                .isEmpty();
        assertThat(context.getBeanNamesForType(org.springframework.kafka.core.KafkaTemplate.class))
                .as("el simulador solo hace POST HTTP")
                .isEmpty();
    }
}
