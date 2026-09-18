package com.rmsolutions.centinela.persistence.repository;

import com.rmsolutions.centinela.domain.Severity;
import com.rmsolutions.centinela.persistence.entity.AlertRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @author Roger Rojas
 * @since 2026-09-18
 */
public interface AlertRuleRepository extends JpaRepository<AlertRule, UUID> {

    Optional<AlertRule> findByPatientIdAndSeverityAndEnabledTrue(UUID patientId, Severity severity);

    List<AlertRule> findByPatientId(UUID patientId);
}
