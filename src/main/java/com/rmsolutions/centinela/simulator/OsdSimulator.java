package com.rmsolutions.centinela.simulator;

import com.rmsolutions.centinela.config.AppProperties;
import com.rmsolutions.centinela.domain.OsdEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Simulador de eventos: sustituye al celular con OSD durante la Fase 1.
 *
 * Al arrancar con el perfil "simulator", genera eventos y los envia por HTTP
 * al webhook del backend, imitando exactamente lo que hara el hardware real.
 * Asi probamos TODO el pipeline (ingesta -> ruteo -> Kafka -> consumidor)
 * sin ningun riesgo, antes de conectar el reloj.
 */
@Component
@Profile("simulator")
public class OsdSimulator implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(OsdSimulator.class);

    private final ScenarioGenerator generator;
    private final AppProperties props;
    private final RestClient client;

    public OsdSimulator(ScenarioGenerator generator, AppProperties props) {
        this.generator = generator;
        this.props = props;
        this.client = RestClient.builder().build();
    }

    @Override
    public void run(String... args) {
        AppProperties.Simulator sim = props.simulator();
        log.info("== SIMULADOR OSD == target={} escenario={} intervalo={}ms cantidad={}",
                sim.targetUrl(), sim.scenario(), sim.intervalMs(), sim.eventCount());

        int count = sim.eventCount();
        int sent = 0;

        while (count < 0 || sent < count) {
            OsdEvent event = generator.next(sim.scenario());
            try {
                client.post()
                        .uri(sim.targetUrl())
                        .header("X-OSD-Token", props.webhookToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(event)
                        .retrieve()
                        .toBodilessEntity();

                log.info("-> enviado #{} | estado={} hr={} bateria={}% conectado={}",
                        sent + 1, event.alarmState(), event.heartRate(),
                        event.batteryLevel(), event.watchConnected());
            } catch (Exception ex) {
                log.error("Fallo enviando evento #{}: {}", sent + 1, ex.getMessage());
            }

            sent++;
            try {
                Thread.sleep(sim.intervalMs());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        log.info("Simulador finalizado. Eventos enviados: {}", sent);
    }
}
