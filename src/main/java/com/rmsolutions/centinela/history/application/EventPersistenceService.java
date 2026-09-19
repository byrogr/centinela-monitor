package com.rmsolutions.centinela.history.application;

import com.rmsolutions.centinela.history.persistence.EventRepository;
import com.rmsolutions.centinela.ingestion.domain.OsdEvent;
import com.rmsolutions.centinela.ingestion.domain.SeverityRouter;
import com.rmsolutions.centinela.registry.domain.Device;
import com.rmsolutions.centinela.registry.domain.Patient;
import com.rmsolutions.centinela.registry.persistence.DeviceRepository;
import com.rmsolutions.centinela.registry.persistence.PatientRepository;
import com.rmsolutions.centinela.shared.domain.DedupKeys;
import com.rmsolutions.centinela.shared.domain.OsdTimeParser;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Caso de uso: guardar un evento de OSD en el registro inmutable.
 *
 * Vive aqui y no dentro del consumidor de Kafka a proposito. Kafka es el medio
 * por el que hoy llega el evento, no la razon de ser de esta logica: la misma
 * operacion la necesitara un reproceso manual o una importacion. El consumidor
 * queda como un adaptador fino que traduce un mensaje en una llamada a este
 * servicio.
 *
 * La severidad NO se recalcula: se reutiliza el SeverityRouter de la ingesta,
 * para que lo que se guarda sea exactamente lo que se ruteo.
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
@Slf4j
@Service
// El perfil simulador no levanta JPA: sin esto, Spring intenta crear el servicio
// alli y falla por falta de repositorios.
@Profile("!simulator")
@RequiredArgsConstructor
public class EventPersistenceService {

    private final PatientRepository patients;
    private final DeviceRepository devices;
    private final EventRepository events;
    private final SeverityRouter router;
    private final OsdTimeParser timeParser;
    private final ObjectMapper jsonMapper;

    /**
     * @param rawJson payload ORIGINAL de OSD, sin reserializar: es lo que se guarda
     *                en raw_payload, y re-serializarlo perderia los campos que el
     *                record OsdEvent no conoce.
     * @param patientCode clave del mensaje en Kafka; identifica al paciente.
     * @param deviceIdHeader dispositivo que el webhook autentico y propago. Puede
     *                       venir vacio en mensajes publicados antes de la tarea 7,
     *                       que siguen en el topico y deben poder reprocesarse.
     * @return true si el evento se guardo; false si era duplicado o no se pudo resolver.
     */
    @Transactional
    public boolean persist(String rawJson, String patientCode, String deviceIdHeader) {
        OsdEvent event;
        try {
            event = jsonMapper.readValue(rawJson, OsdEvent.class);
        } catch (RuntimeException e) {
            // Mensaje ilegible: no hay nada que persistir y reintentar no lo arreglara.
            return discard(patientCode, "el payload no es un evento OSD valido: " + e.getMessage(), rawJson);
        }

        Optional<Instant> eventTime = timeParser.toInstant(event.time());
        if (eventTime.isEmpty()) {
            return discard(patientCode, "el campo 'Time' no es interpretable: '" + event.time() + "'", rawJson);
        }

        Optional<Device> device = resolveDevice(patientCode, deviceIdHeader);
        if (device.isEmpty()) {
            return false;
        }
        UUID patientId = device.get().getPatient().getId();

        String dedupKey = DedupKeys.of(device.get().getId(), eventTime.get(), event.alarmState());
        String severity = router.route(event).severity().name();

        int rows = events.insertIfAbsent(
                device.get().getId(),
                patientId,
                eventTime.get(),
                event.alarmState(),
                severity,
                event.heartRate(),
                event.batteryLevel(),
                event.watchConnected(),
                rawJson,
                dedupKey);

        if (rows == 0) {
            log.debug("Evento duplicado descartado | paciente={} dedupKey={}", patientCode, dedupKey);
            return false;
        }
        log.debug("Evento persistido | paciente={} severidad={} dedupKey={}",
                patientCode, severity, dedupKey);
        return true;
    }

    /**
     * Resuelve el dispositivo emisor.
     *
     * La via buena es la cabecera que propaga el webhook, que ya autentico al
     * dispositivo. El respaldo por paciente solo se usa con mensajes anteriores a
     * la tarea 7, que siguen en el topico con su retencion y deben poder
     * reprocesarse; con mas de un dispositivo activo ese respaldo no puede acertar.
     */
    private Optional<Device> resolveDevice(String patientCode, String deviceIdHeader) {
        if (deviceIdHeader != null && !deviceIdHeader.isBlank()) {
            return resolveFromHeader(deviceIdHeader);
        }
        return patients.findByCode(patientCode)
                .map(this::resolveFromPatient)
                .orElseGet(() -> {
                    log.error("Evento sin persistir | no hay ningun paciente con code={}", patientCode);
                    return Optional.empty();
                });
    }

    private Optional<Device> resolveFromHeader(String deviceIdHeader) {
        UUID deviceId;
        try {
            deviceId = UUID.fromString(deviceIdHeader);
        } catch (IllegalArgumentException e) {
            log.error("Evento sin persistir | la cabecera de dispositivo no es un UUID: '{}'",
                    deviceIdHeader);
            return Optional.empty();
        }
        Optional<Device> device = devices.findById(deviceId);
        if (device.isEmpty()) {
            log.error("Evento sin persistir | la cabecera apunta a un dispositivo inexistente: {}",
                    deviceId);
        }
        return device;
    }

    private Optional<Device> resolveFromPatient(Patient patient) {
        List<Device> active = devices.findByPatientId(patient.getId()).stream()
                .filter(Device::isActive)
                .toList();

        if (active.size() == 1) {
            return Optional.of(active.getFirst());
        }
        if (active.isEmpty()) {
            log.error("Evento sin persistir | paciente={} no tiene ningun dispositivo activo registrado",
                    patient.getCode());
        } else {
            log.error("Evento sin persistir | paciente={} tiene {} dispositivos activos y el mensaje "
                            + "no dice cual lo emitio; atribuirlo al azar falsearia el historial",
                    patient.getCode(), active.size());
        }
        return Optional.empty();
    }

    /**
     * Descarta un mensaje que no se puede persistir por un problema de datos o de
     * configuracion, no por una caida transitoria.
     *
     * Se registra a ERROR y se deja que el consumidor confirme el offset en vez de
     * reintentar en bucle, por dos razones: reintentar no arregla un dato mal
     * formado, y bloquear la particion detendria la persistencia de TODOS los
     * eventos de ese nino.
     *
     * Descartar aqui no silencia ninguna alerta: el evento sigue en 'osd.events.raw'
     * con su retencion, listo para reprocesarse, y el camino critico que avisa a los
     * cuidadores corre por otro consumer group que no depende de esto.
     */
    private boolean discard(String childId, String reason, String rawJson) {
        log.error("Evento sin persistir | childId={} motivo={} payload={}", childId, reason, rawJson);
        return false;
    }
}
