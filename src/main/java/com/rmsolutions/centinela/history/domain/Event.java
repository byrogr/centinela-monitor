package com.rmsolutions.centinela.history.domain;

import com.rmsolutions.centinela.shared.domain.Severity;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Evento de OSD ya persistido. Es el registro consultable que alimentara el backoffice.
 *
 * <p>Tres decisiones que se apartan del resto de entidades:
 *
 *  <p>1. {@code @Immutable} y ningun setter: la tabla es un log append-only y un
 *     trigger en la base rechaza UPDATE y DELETE. Marcarla asi evita que Hibernate
 *     intente siquiera un UPDATE por dirty-checking, que fallaria en la base.
 *
 *  <p>2. FK como UUID planos en vez de {@code @ManyToOne}: esta es la ruta caliente
 *     (un evento cada pocos segundos por dispositivo) y solo necesitamos guardar
 *     el identificador. Cargar entidades completas seria trabajo inutil.
 *
 *  <p>3. La ESCRITURA no pasa por esta entidad: va por el INSERT ... ON CONFLICT DO NOTHING
 *     del repositorio, porque la idempotencia la garantiza la base, no el codigo.
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
@Entity
@Table(name = "event")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)   // requerido por JPA
public class Event {

    @EmbeddedId
    private EventId id;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    /**
     * Codigo crudo de OSD (0=OK, 1=WARNING, 2=ALARM, 3=FALL...). Se guarda sin
     * traducir: un codigo desconocido no debe perderse. La lectura semantica va
     * en {@link com.rmsolutions.centinela.ingestion.domain.AlarmState}.
     */
    @Column(name = "alarm_state", nullable = false)
    private int alarmState;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Column(name = "heart_rate")
    private Integer heartRate;

    @Column(name = "battery_level")
    private Integer batteryLevel;

    @Column(name = "watch_connected")
    private Boolean watchConnected;

    /** Payload original completo de OSD, por si aparecen campos nuevos en otra version. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", nullable = false)
    private String rawPayload;

    @Column(name = "dedup_key", nullable = false)
    private String dedupKey;

    @Column(name = "ingested_at", nullable = false, insertable = false, updatable = false)
    private Instant ingestedAt;

    /** Atajo: la marca de tiempo vive dentro de la clave compuesta. */
    public Instant getEventTime() {
        return id.eventTime();
    }
}
