package com.rmsolutions.centinela.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Menor monitoreado.
 *
 * PRIVACIDAD: se guarda solo el anio de nacimiento, nunca la fecha exacta.
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
@Entity
@Table(name = "patient")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)   // requerido por JPA
public class Patient {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    /**
     * Identificador estable del paciente: es el childId de la Fase 1 y viaja
     * como clave de particion en Kafka. El consumidor de persistencia lo usa
     * para resolver a que paciente pertenece cada mensaje.
     */
    @Column(nullable = false, unique = true)
    private String code;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "birth_year")
    private Integer birthYear;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    public Patient(Account account, String code, String displayName, Integer birthYear) {
        this.account = account;
        this.code = code;
        this.displayName = displayName;
        this.birthYear = birthYear;
    }
}
