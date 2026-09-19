package com.rmsolutions.centinela.registry.persistence;

import com.rmsolutions.centinela.registry.domain.Device;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @author Roger Rojas
 * @since 2026-09-18
 */
public interface DeviceRepository extends JpaRepository<Device, UUID> {

    /**
     * Verificacion de la API key del webhook: se busca por HASH, nunca por la
     * clave en claro, que no existe en la base.
     */
    Optional<Device> findByApiKeyHashAndActiveTrue(String apiKeyHash);

    /** Dispositivos que el vigilante de silencio debe supervisar. */
    List<Device> findByActiveTrue();

    List<Device> findByPatientId(UUID patientId);
}
