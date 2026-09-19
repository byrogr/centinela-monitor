package com.rmsolutions.centinela.simulator;

import com.rmsolutions.centinela.ingestion.domain.OsdEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Simulador de eventos: sustituye al celular con OSD durante la Fase 1.
 *
 * <p>Al arrancar con el perfil "simulator", genera eventos y los envia por HTTP
 * al webhook del backend, imitando exactamente lo que hara el hardware real.
 * Asi probamos TODO el pipeline (ingesta -> ruteo -> Kafka -> consumidor)
 * sin ningun riesgo, antes de conectar el reloj.
 */
@Component
@Profile("simulator")
@Slf4j
public class OsdSimulator implements CommandLineRunner {

    private final ScenarioGenerator generator;
    private final SimulatorProperties sim;
    private final RestClient client;

    public OsdSimulator(ScenarioGenerator generator, SimulatorProperties sim) {
        this.generator = generator;
        this.sim = sim;
        this.client = RestClient.builder().build();
    }

    @Override
    public void run(String... args) {
        log.info("== SIMULADOR OSD == target={} escenario={} intervalo={}ms cantidad={}",
                sim.targetUrl(), sim.scenario(), sim.intervalMs(), sim.eventCount());

        int count = sim.eventCount();
        int sent = 0;

        while (count < 0 || sent < count) {
            OsdEvent event = generator.next(sim.scenario());
            try {
                client.post()
                        .uri(sim.targetUrl())
                        .header("X-Device-Key", sim.apiKey())
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
