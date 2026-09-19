package com.rmsolutions.centinela.alerting.application;

import com.rmsolutions.centinela.ingestion.domain.OsdEvent;
import com.rmsolutions.centinela.shared.domain.Severity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Reparte un aviso por todos los canales que lo admitan.
 * <p>
 * FAIL-SAFE: un canal que falla NO impide que los demas lo intenten. Si el
 * proveedor de SMS esta caido, la llamada de voz y el push tienen que salir
 * igual; propagar la excepcion del primero dejaria al cuidador sin ningun aviso
 * por culpa de un proveedor. Por eso cada entrega va en su propio try.
 *
 * @author Roger Rojas
 * @since 2026-09-19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final List<NotificationChannel> channels;

    public void dispatch(Severity severity, OsdEvent event) {
        boolean delivered = false;

        for (NotificationChannel channel : channels) {
            if (!channel.supports(severity)) {
                continue;
            }
            try {
                channel.deliver(severity, event);
                delivered = true;
            } catch (RuntimeException e) {
                log.error("Fallo el canal '{}' al avisar de un evento {} | causa={}",
                        channel.name(), severity, e.getMessage(), e);
            }
        }

        if (!delivered && severity != Severity.INFO) {
            // Nadie pudo avisar de algo que lo merecia: es lo mas grave que puede
            // pasar en este sistema, asi que no puede quedarse sin registrar.
            log.error("NINGUN canal pudo entregar un aviso {} | evento={}", severity, event);
        }
    }
}
