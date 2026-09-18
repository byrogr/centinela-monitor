package com.rmsolutions.centinela.simulator;

import com.rmsolutions.centinela.domain.OsdEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Fabrica de eventos OSD sinteticos pero verosimiles.
 *
 * El escenario SEIZURE reproduce una LINEA DE TIEMPO clinica plausible en
 * ciclos de 15 pasos: basal -> pre-ictal -> convulsion -> recuperacion.
 * Asi validamos que el pipeline detecta la transicion, no solo un valor aislado.
 *
 * Es 'stateful': lleva un contador de pasos y drena la bateria lentamente.
 */
@Component
@Profile("simulator")
public class ScenarioGenerator {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private int step = 0;
    private int battery = 92;

    public OsdEvent next(Scenario scenario) {
        Scenario effective = (scenario == Scenario.RANDOM) ? pickRandom() : scenario;

        OsdEvent event = switch (effective) {
            case NORMAL -> normal();
            case SEIZURE -> seizureTimeline();
            case FALL -> fall();
            case LOW_BATTERY -> lowBattery();
            case DISCONNECT -> disconnect();
            case RANDOM -> normal(); // no ocurre; RANDOM ya se resolvio arriba
        };

        step++;
        // Drenaje lento de bateria (cada 3 eventos, 1%).
        if (battery > 1 && step % 3 == 0) {
            battery--;
        }
        return event;
    }

    /** Convulsion como secuencia temporal en ciclos de 15 pasos. */
    private OsdEvent seizureTimeline() {
        int phase = step % 15;
        if (phase <= 4) {
            return normal();                    // basal
        } else if (phase <= 6) {
            return preIctal();                  // aviso previo
        } else if (phase <= 11) {
            return alarm();                     // convulsion
        } else {
            return recovery();                  // post-ictal
        }
    }

    private OsdEvent normal() {
        return build(0, "OK",
                rd(0.5, 3.0), rd(80, 350), rd(200, 800), rd(100, 500),
                ri(66, 92), true);
    }

    private OsdEvent preIctal() {
        return build(1, "WARNING",
                rd(3.0, 4.5), rd(400, 850), rd(1500, 2800), rd(1200, 2200),
                ri(96, 122), true);
    }

    private OsdEvent alarm() {
        return build(2, "ALARM",
                rd(4.5, 6.2), rd(1000, 2100), rd(3800, 6500), rd(3200, 5200),
                ri(126, 185), true);
    }

    private OsdEvent recovery() {
        return build(0, "OK",
                rd(2.0, 3.5), rd(300, 600), rd(900, 1600), rd(700, 1300),
                ri(98, 120), true); // HR aun elevada, descendiendo
    }

    private OsdEvent fall() {
        return build(3, "FALL",
                rd(5.0, 7.0), rd(1500, 2600), rd(4000, 7000), rd(3500, 6000),
                ri(110, 160), true);
    }

    private OsdEvent lowBattery() {
        battery = Math.min(battery, ri(4, 11)); // fuerza bateria critica
        return build(0, "OK",
                rd(0.5, 3.0), rd(80, 350), rd(200, 800), rd(100, 500),
                ri(66, 92), true);
    }

    private OsdEvent disconnect() {
        // Sin telemetria valida: valores en cero y watchConnected=false.
        return build(0, "OK", 0, 0, 0, 0, null, false);
    }

    private OsdEvent build(int alarmState, String phrase,
                           double maxFreq, double maxVal, double specPower, double roiPower,
                           Integer heartRate, boolean connected) {
        return new OsdEvent(
                LocalDateTime.now().format(FMT),
                alarmState,
                phrase,
                round(maxFreq),
                round(maxVal),
                round(specPower),
                round(roiPower),
                heartRate,
                battery,
                connected
        );
    }

    private Scenario pickRandom() {
        Scenario[] pool = {
                Scenario.NORMAL, Scenario.NORMAL, Scenario.NORMAL, // sesgo hacia lo normal
                Scenario.SEIZURE, Scenario.FALL,
                Scenario.LOW_BATTERY, Scenario.DISCONNECT
        };
        return pool[ThreadLocalRandom.current().nextInt(pool.length)];
    }

    private double rd(double min, double max) {
        return ThreadLocalRandom.current().nextDouble(min, max);
    }

    private int ri(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
