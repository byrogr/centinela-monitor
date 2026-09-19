package com.rmsolutions.centinela.watchdog.domain;

import com.rmsolutions.centinela.registry.domain.Device;

import java.time.Instant;
import java.util.Optional;

/**
 * Una forma de averiguar cuando se supo por ultima vez de un dispositivo.
 *
 * <p>El vigilante consulta las fuentes EN ORDEN y se queda con la primera que
 * responde, de mas fresca a mas conservadora. Esta es la abstraccion que si se
 * gana su sitio: hay tres implementaciones reales, y anadir una cuarta (por
 * ejemplo, un ping del propio reloj) no obliga a tocar el vigilante.
 *
 * <p>Contrato: devolver vacio significa "no lo se", NUNCA "lleva mucho callado".
 * Quien no sabe, deja pasar el turno a la siguiente fuente.
 *
 * @author Roger Rojas
 * @since 2026-09-19
 */
public interface SignalSource {

    Optional<Instant> lastSignal(Device device, Instant now);

    /** Para explicar en los logs de donde salio el dato. */
    String description();
}
