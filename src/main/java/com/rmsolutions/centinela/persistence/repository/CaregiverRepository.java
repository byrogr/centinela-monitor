package com.rmsolutions.centinela.persistence.repository;

import com.rmsolutions.centinela.persistence.entity.Caregiver;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * @author Roger Rojas
 * @since 2026-09-18
 */
public interface CaregiverRepository extends JpaRepository<Caregiver, UUID> {

    List<Caregiver> findByAccountId(UUID accountId);
}
