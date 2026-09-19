package com.rmsolutions.centinela.registry.domain;

import com.rmsolutions.centinela.shared.domain.Severity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Regla de alerta de un paciente para una severidad concreta.
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
@Entity
@Table(name = "alert_rule")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)   // requerido por JPA
public class AlertRule {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    /** Segundos sin confirmacion antes de pasar al siguiente cuidador (Fase 3). */
    @Setter
    @Column(name = "escalate_after_seconds", nullable = false)
    private int escalateAfterSeconds;

    @Setter
    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    public AlertRule(Patient patient, Severity severity, int escalateAfterSeconds) {
        this.patient = patient;
        this.severity = severity;
        this.escalateAfterSeconds = escalateAfterSeconds;
        this.enabled = true;
    }
}
