package com.rmsolutions.centinela.registry.persistence;

import com.rmsolutions.centinela.registry.domain.CaregiverLink;
import com.rmsolutions.centinela.registry.domain.CaregiverLinkId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @author Roger Rojas
 * @since 2026-09-18
 */
public interface CaregiverLinkRepository extends JpaRepository<CaregiverLink, CaregiverLinkId> {

    /** Cadena de escalado de un paciente, en el orden en que hay que avisar. */
    List<CaregiverLink> findByIdPatientIdOrderByEscalationOrderAsc(UUID patientId);
}
