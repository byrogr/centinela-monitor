package com.rmsolutions.centinela.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Familia: agrupa pacientes y cuidadores, y es la frontera de aislamiento de datos.
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
@Entity
@Table(name = "account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)   // requerido por JPA
public class Account {

    @Id
    @GeneratedValue
    private UUID id;

    @Setter
    @Column(nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    public Account(String name) {
        this.name = name;
    }
}
