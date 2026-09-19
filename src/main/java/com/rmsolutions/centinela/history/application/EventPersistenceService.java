package com.rmsolutions.centinela.history.application;

import com.rmsolutions.centinela.history.domain.DedupKeys;
import com.rmsolutions.centinela.history.domain.OsdTimeParser;
import com.rmsolutions.centinela.history.persistence.EventRepository;
import com.rmsolutions.centinela.ingestion.domain.OsdEvent;
import com.rmsolutions.centinela.registry.domain.Device;
import com.rmsolutions.centinela.registry.domain.Patient;
import com.rmsolutions.centinela.registry.persistence.DeviceRepository;
import com.rmsolutions.centinela.registry.persistence.PatientRepository;
import com.rmsolutions.centinela.ingestion.domain.SeverityRouter;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
     * @param childId clave del mensaje en Kafka; identifica al paciente.
     * @return true si el evento se guardo; false si era duplicado o no se pudo resolver.
     */
    @Transactional
    public boolean persistir(String rawJson, String childId) {
        OsdEvent event;
        try {
            event = jsonMapper.readValue(rawJson, OsdEvent.class);
        } catch (RuntimeException e) {
            // Mensaje ilegible: no hay nada que persistir y reintentar no lo arreglara.
            return descartar(childId, "el payload no es un evento OSD valido: " + e.getMessage(), rawJson);
        }

        Optional<Instant> eventTime = timeParser.aInstante(event.time());
        if (eventTime.isEmpty()) {
            return descartar(childId, "el campo 'Time' no es interpretable: '" + event.time() + "'", rawJson);
        }

        Optional<Patient> patient = patients.findByCode(childId);
        if (patient.isEmpty()) {
            return descartar(childId, "no hay ningun paciente registrado con ese code", rawJson);
        }

        Optional<Device> device = resolverDispositivo(patient.get());
        if (device.isEmpty()) {
            return false;
        }

        String dedupKey = DedupKeys.de(device.get().getId(), eventTime.get(), event.alarmState());
        String severity = router.route(event).severity().name();

        int filas = events.insertarSiNoExiste(
                device.get().getId(),
                patient.get().getId(),
                eventTime.get(),
                event.alarmState(),
                severity,
                event.heartRate(),
                event.batteryLevel(),
                event.watchConnected(),
                rawJson,
                dedupKey);

        if (filas == 0) {
            log.debug("Evento duplicado descartado | childId={} dedupKey={}", childId, dedupKey);
            return false;
        }
        log.debug("Evento persistido | childId={} severidad={} dedupKey={}", childId, severity, dedupKey);
        return true;
    }

    /**
     * Resuelve el dispositivo emisor.
     *
     * De momento se deriva del paciente, porque el mensaje de la Fase 1 solo lleva
     * el childId como clave. A partir de la tarea 7 el webhook autentica con
     * X-Device-Key y podra propagar la identidad del dispositivo directamente;
     * esta resolucion pasara a ser el respaldo.
     */
    private Optional<Device> resolverDispositivo(Patient patient) {
        List<Device> activos = devices.findByPatientId(patient.getId()).stream()
                .filter(Device::isActive)
                .toList();

        if (activos.size() == 1) {
            return Optional.of(activos.getFirst());
        }
        if (activos.isEmpty()) {
            log.error("Evento sin persistir | paciente={} no tiene ningun dispositivo activo registrado",
                    patient.getCode());
        } else {
            log.error("Evento sin persistir | paciente={} tiene {} dispositivos activos y el mensaje "
                            + "no dice cual lo emitio; atribuirlo al azar falsearia el historial",
                    patient.getCode(), activos.size());
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
    private boolean descartar(String childId, String motivo, String rawJson) {
        log.error("Evento sin persistir | childId={} motivo={} payload={}", childId, motivo, rawJson);
        return false;
    }
}
