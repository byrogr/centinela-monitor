package com.rmsolutions.centinela.watchdog.persistence;

import com.rmsolutions.centinela.watchdog.domain.SilenceIncident;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @author Roger Rojas
 * @since 2026-09-18
 */
public interface SilenceIncidentRepository extends JpaRepository<SilenceIncident, UUID> {

    /**
     * Incidencia abierta de un dispositivo, si la hay. La base garantiza que
     * como maximo haya una (indice unico parcial), de ahi el Optional.
     */
    Optional<SilenceIncident> findByDeviceIdAndClosedAtIsNull(UUID deviceId);

    /** Todas las incidencias abiertas: lo que el vigilante revisa en cada ciclo. */
    List<SilenceIncident> findByClosedAtIsNull();
}
