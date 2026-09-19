package com.rmsolutions.centinela.registry.persistence;

import com.rmsolutions.centinela.registry.domain.Caregiver;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @author Roger Rojas
 * @since 2026-09-18
 */
public interface CaregiverRepository extends JpaRepository<Caregiver, UUID> {

    List<Caregiver> findByAccountId(UUID accountId);
}
