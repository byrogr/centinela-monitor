package com.rmsolutions.centinela.persistence.repository;

import com.rmsolutions.centinela.persistence.entity.SilenceIncident;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
