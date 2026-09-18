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
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Cuidador que recibe las alertas.
 *
 * La base exige email o telefono: un cuidador sin ningun canal de contacto
 * seria un eslabon muerto en la cadena de escalado.
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
@Entity
@Table(name = "caregiver")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)   // requerido por JPA
public class Caregiver {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Setter
    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Setter
    private String email;

    @Setter
    private String phone;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    public Caregiver(Account account, String displayName, String email, String phone) {
        this.account = account;
        this.displayName = displayName;
        this.email = email;
        this.phone = phone;
    }
}
