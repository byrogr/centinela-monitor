package com.rmsolutions.centinela.registry.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Relacion N:M cuidador-paciente con el orden de la cadena de escalado.
 *
 * 'escalation_order' es unico por paciente: a quien se avisa primero no puede
 * ser ambiguo cuando hay una emergencia.
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
@Entity
@Table(name = "caregiver_link")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)   // requerido por JPA
public class CaregiverLink {

    @EmbeddedId
    private CaregiverLinkId id;

    @Setter
    @Column(name = "escalation_order", nullable = false)
    private int escalationOrder;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    public CaregiverLink(UUID caregiverId, UUID patientId, int escalationOrder) {
        this.id = new CaregiverLinkId(caregiverId, patientId);
        this.escalationOrder = escalationOrder;
    }
}
