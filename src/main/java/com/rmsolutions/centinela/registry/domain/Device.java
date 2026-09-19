package com.rmsolutions.centinela.registry.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Dispositivo emisor: el celular con OSD emparejado al reloj.
 *
 * <p>SEGURIDAD: solo se guarda el hash de la API key. La clave en claro se muestra
 * una unica vez al emitirla y nunca se persiste, por eso 'apiKeyHash' no tiene setter.
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
@Entity
@Table(name = "device")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)   // requerido por JPA
public class Device {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Setter
    @Column(nullable = false)
    private String label;

    @Column(name = "api_key_hash", nullable = false, unique = true)
    private String apiKeyHash;

    /** Primeros caracteres de la clave: identifica cual es sin revelarla. */
    @Column(name = "api_key_prefix", nullable = false)
    private String apiKeyPrefix;

    /**
     * Segundos sin senal antes de que el vigilante abra una incidencia.
     * Configurable por dispositivo; el rango acordado es 3-5 min.
     */
    @Setter
    @Column(name = "silence_threshold_seconds", nullable = false)
    private int silenceThresholdSeconds;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public Device(Patient patient, String label, String apiKeyHash, String apiKeyPrefix,
                  int silenceThresholdSeconds) {
        this.patient = patient;
        this.label = label;
        this.apiKeyHash = apiKeyHash;
        this.apiKeyPrefix = apiKeyPrefix;
        this.silenceThresholdSeconds = silenceThresholdSeconds;
        this.active = true;
    }

    /**
     * Revoca el dispositivo. Se hace por metodo y no por setters sueltos para que
     * 'active' y 'revokedAt' no puedan quedar en un estado incoherente.
     */
    public void revoke(Instant when) {
        this.active = false;
        this.revokedAt = when;
    }
}
