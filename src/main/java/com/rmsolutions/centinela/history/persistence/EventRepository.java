package com.rmsolutions.centinela.history.persistence;

import com.rmsolutions.centinela.history.domain.Event;
import com.rmsolutions.centinela.history.domain.EventId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Acceso al log de eventos.
 *
 * El repositorio NO expone save(): escribir un evento va siempre por
 * {@link #insertIfAbsent}, que delega la idempotencia en la base.
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
public interface EventRepository extends JpaRepository<Event, EventId> {

    /**
     * Inserta un evento de forma idempotente.
     *
     * El ON CONFLICT hace que reprocesar el mismo mensaje de Kafka (un rebalanceo,
     * un reintento, un replay) no duplique nada. Es la base quien decide, no el
     * codigo: dos consumidores concurrentes con el mismo evento no pueden colarse
     * entre un SELECT y un INSERT porque aqui no hay SELECT previo.
     *
     * @return 1 si el evento se inserto, 0 si ya existia (duplicado descartado).
     */
    @Modifying
    @Query(value = """
            INSERT INTO event (device_id, patient_id, event_time, alarm_state, severity,
                               heart_rate, battery_level, watch_connected, raw_payload, dedup_key)
            VALUES (:deviceId, :patientId, :eventTime, :alarmState, :severity,
                    :heartRate, :batteryLevel, :watchConnected, CAST(:rawPayload AS jsonb), :dedupKey)
            ON CONFLICT (dedup_key, event_time) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("deviceId") UUID deviceId,
                           @Param("patientId") UUID patientId,
                           @Param("eventTime") Instant eventTime,
                           @Param("alarmState") int alarmState,
                           @Param("severity") String severity,
                           @Param("heartRate") Integer heartRate,
                           @Param("batteryLevel") Integer batteryLevel,
                           @Param("watchConnected") Boolean watchConnected,
                           @Param("rawPayload") String rawPayload,
                           @Param("dedupKey") String dedupKey);

    /**
     * Historial de un paciente en una ventana de tiempo.
     * Acotar por tiempo no es opcional: es lo que permite a PostgreSQL descartar
     * particiones enteras en vez de recorrer todo el historial.
     */
    @Query("""
            SELECT e FROM Event e
             WHERE e.patientId = :patientId
               AND e.id.eventTime >= :from
               AND e.id.eventTime < :to
             ORDER BY e.id.eventTime DESC
            """)
    List<Event> findPatientHistory(@Param("patientId") UUID patientId,
                                    @Param("from") Instant from,
                                    @Param("to") Instant to,
                                    Pageable pageable);

    /**
     * Ultimo evento conocido de un dispositivo.
     *
     * El vigilante de silencio usa Redis para el last_seen, pero si Redis esta
     * caido o frio esta consulta es el respaldo: preferimos preguntar a la base
     * antes que asumir un estado desconocido y no vigilar.
     */
    @Query("""
            SELECT e FROM Event e
             WHERE e.deviceId = :deviceId
               AND e.id.eventTime >= :from
             ORDER BY e.id.eventTime DESC
             LIMIT 1
            """)
    Optional<Event> findLastDeviceEvent(@Param("deviceId") UUID deviceId,
                                              @Param("from") Instant from);
}
