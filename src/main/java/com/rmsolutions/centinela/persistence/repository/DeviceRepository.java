package com.rmsolutions.centinela.persistence.repository;

import com.rmsolutions.centinela.persistence.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
