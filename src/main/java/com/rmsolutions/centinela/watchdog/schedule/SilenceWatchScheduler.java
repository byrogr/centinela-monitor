package com.rmsolutions.centinela.watchdog.schedule;

import com.rmsolutions.centinela.watchdog.application.SilenceWatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dispara la revision del vigilante cada cierto tiempo.
 *
 * Adaptador de entrada: no decide nada, solo marca el ritmo.
 *
 * Se usa fixedDelay y no fixedRate: si una pasada tarda mas de lo previsto,
 * fixedRate encolaria ejecuciones y acabarian solapandose sobre los mismos
 * dispositivos. El intervalo debe ser bastante menor que el umbral de silencio
 * (240s por defecto) para no sumar su propio retraso al aviso.
 *
 * @author Roger Rojas
 * @since 2026-09-19
 */
@Slf4j
@Component
@Profile("!simulator")
@RequiredArgsConstructor
public class SilenceWatchScheduler {

    private final SilenceWatchService watch;

    @Scheduled(
            fixedDelayString = "${app.watchdog.check-interval-ms:30000}",
            initialDelayString = "${app.watchdog.initial-delay-ms:20000}")
    public void check() {
        try {
            int opened = watch.scan();
            if (opened > 0) {
                log.warn("Vigilante de silencio: {} incidencia(s) abiertas en esta pasada", opened);
            }
        } catch (RuntimeException e) {
            // Si una excepcion escapara, Spring cancelaria la tarea programada y el
            // vigilante dejaria de vigilar en silencio, que es el peor fallo posible.
            log.error("Fallo la pasada del vigilante de silencio | causa={}", e.getMessage(), e);
        }
    }
}
