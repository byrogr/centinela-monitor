package com.rmsolutions.centinela.history.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Clave compuesta de {@link Event}.
 *
 * <p>'event_time' forma parte de la clave porque es la clave de particion:
 * PostgreSQL exige que toda restriccion unica de una tabla particionada la incluya.
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
@Embeddable
public record EventId(
        UUID id,
        @Column(name = "event_time") Instant eventTime
) implements Serializable {
}
