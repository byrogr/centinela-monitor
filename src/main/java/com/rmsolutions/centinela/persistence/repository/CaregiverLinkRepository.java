package com.rmsolutions.centinela.persistence.repository;

import com.rmsolutions.centinela.persistence.entity.CaregiverLink;
import com.rmsolutions.centinela.persistence.entity.CaregiverLinkId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * @author Roger Rojas
 * @since 2026-09-18
 */
public interface CaregiverLinkRepository extends JpaRepository<CaregiverLink, CaregiverLinkId> {

    /** Cadena de escalado de un paciente, en el orden en que hay que avisar. */
    List<CaregiverLink> findByIdPatientIdOrderByEscalationOrderAsc(UUID patientId);
}
