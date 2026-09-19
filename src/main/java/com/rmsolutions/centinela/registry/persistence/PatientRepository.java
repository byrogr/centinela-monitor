package com.rmsolutions.centinela.registry.persistence;

import com.rmsolutions.centinela.registry.domain.Patient;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @author Roger Rojas
 * @since 2026-09-18
 */
public interface PatientRepository extends JpaRepository<Patient, UUID> {

    /**
     * Resuelve el paciente a partir del childId que viaja como clave en Kafka.
     * Es el puente entre el pipeline de la Fase 1 y el modelo relacional.
     */
    Optional<Patient> findByCode(String code);

    List<Patient> findByAccountId(UUID accountId);
}
