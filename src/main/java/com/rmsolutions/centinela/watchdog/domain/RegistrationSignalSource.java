package com.rmsolutions.centinela.watchdog.domain;

import com.rmsolutions.centinela.registry.domain.Device;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * Ultimo recurso: la fecha de alta del dispositivo.
 *
 * <p>Cubre al reloj que NUNCA llego a emitir. Sin esta fuente quedaria fuera de la
 * vigilancia para siempre, que es justo el fallo silencioso que el vigilante
 * existe para evitar: la familia creeria estar monitorizada sin estarlo.
 *
 * <p>Contar desde el alta le da el mismo margen que a cualquier otro dispositivo,
 * en vez de alarmar en el instante de registrarlo.
 *
 * <p>Siempre responde, asi que cierra la cadena.
 *
 * @author Roger Rojas
 * @since 2026-09-19
 */
@Component
@Profile("!simulator")
@Order(3)
public class RegistrationSignalSource implements SignalSource {

    @Override
    public Optional<Instant> lastSignal(Device device, Instant now) {
        return Optional.ofNullable(device.getCreatedAt());
    }

    @Override
    public String description() {
        return "fecha de alta del dispositivo (nunca emitio)";
    }
}
