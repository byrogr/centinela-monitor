package com.rmsolutions.centinela.persistence.entity;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.UUID;

/**
 * Clave compuesta de {@link CaregiverLink}.
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
@Embeddable
public record CaregiverLinkId(UUID caregiverId, UUID patientId) implements Serializable {
}
