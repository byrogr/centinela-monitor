package com.rmsolutions.centinela.watchdog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Incidencia de perdida de senal, abierta por el vigilante de silencio.
 *
 * Es la memoria DURABLE del vigilante: Redis solo guarda el last_seen, que es
 * cache volatil. Un indice unico parcial en la base garantiza como maximo una
 * incidencia abierta por dispositivo, de modo que el vigilante no repita la
 * alerta en cada ciclo del scheduler.
 *
 * Igual que {@link Event}, usa FK como UUID planos: la escribe una tarea de
 * fondo que no necesita navegar el grafo de entidades.
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
@Entity
@Table(name = "silence_incident")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)   // requerido por JPA
public class SilenceIncident {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    /** Ultimo evento conocido antes del silencio; null si nunca hubo senal. */
    @Column(name = "last_event_time")
    private Instant lastEventTime;

    @Column(name = "threshold_seconds", nullable = false)
    private int thresholdSeconds;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    /** Evento sintetico inyectado al pipeline al abrir la incidencia. */
    @Setter
    @Column(name = "synthetic_dedup_key")
    private String syntheticDedupKey;

    public SilenceIncident(UUID deviceId, UUID patientId, Instant lastEventTime,
                           int thresholdSeconds, Instant openedAt) {
        this.deviceId = deviceId;
        this.patientId = patientId;
        this.lastEventTime = lastEventTime;
        this.thresholdSeconds = thresholdSeconds;
        this.openedAt = openedAt;
    }

    public boolean estaAbierta() {
        return closedAt == null;
    }

    /** Cierra la incidencia cuando el dispositivo vuelve a dar senal. */
    public void cerrar(Instant cuando) {
        this.closedAt = cuando;
    }
}
