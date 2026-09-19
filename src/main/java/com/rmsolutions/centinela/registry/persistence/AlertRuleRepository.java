package com.rmsolutions.centinela.registry.persistence;

import com.rmsolutions.centinela.registry.domain.AlertRule;
import com.rmsolutions.centinela.shared.domain.Severity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @author Roger Rojas
 * @since 2026-09-18
 */
public interface AlertRuleRepository extends JpaRepository<AlertRule, UUID> {

    Optional<AlertRule> findByPatientIdAndSeverityAndEnabledTrue(UUID patientId, Severity severity);

    List<AlertRule> findByPatientId(UUID patientId);
}
