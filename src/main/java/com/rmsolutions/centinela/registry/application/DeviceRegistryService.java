package com.rmsolutions.centinela.registry.application;

import com.rmsolutions.centinela.registry.domain.ApiKeys;
import com.rmsolutions.centinela.registry.domain.Device;
import com.rmsolutions.centinela.registry.domain.Patient;
import com.rmsolutions.centinela.registry.persistence.DeviceRepository;
import com.rmsolutions.centinela.registry.persistence.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Ciclo de vida de las credenciales de un dispositivo: alta, verificacion y revocacion.
 *
 * SOBRE LA VERIFICACION Y EL FAIL-SAFE: este es el unico punto del sistema donde
 * "ante la duda, escalar" NO aplica. Aqui se falla CERRADO. Aceptar un dispositivo
 * que no se ha podido autenticar permitiria a cualquiera inyectar eventos, y el
 * peligro no es solo una alarma falsa: bastaria con inyectar telemetria "todo OK"
 * para que el vigilante de silencio creyera que el reloj sigue enviando senal
 * mientras el nino esta sin supervision. Una credencial que no verifica se rechaza.
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
@Slf4j
@Service
@Profile("!simulator")
@RequiredArgsConstructor
public class DeviceRegistryService {

    private final DeviceRepository devices;
    private final PatientRepository patients;

    /**
     * Da de alta un dispositivo y emite su API key.
     *
     * @return el dispositivo creado junto con la clave en claro, que solo se
     * entrega en esta llamada.
     */
    @Transactional
    public RegisteredDevice register(UUID patientId, String label, int silenceThresholdSeconds) {
        Patient patient = patients.findById(patientId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el paciente " + patientId));

        String apiKey = ApiKeys.generate();
        Device device = devices.save(new Device(
                patient,
                label,
                ApiKeys.hash(apiKey),
                ApiKeys.prefix(apiKey),
                silenceThresholdSeconds));

        // Se registra el prefijo, nunca la clave.
        log.info("Dispositivo registrado | paciente={} label='{}' deviceId={} apiKeyPrefix={}",
                patient.getCode(), label, device.getId(), device.getApiKeyPrefix());

        return new RegisteredDevice(
                device.getId(), device.getLabel(), apiKey, device.getApiKeyPrefix());
    }

    /**
     * Verifica una API key presentada por un dispositivo.
     *
     * La busqueda es POR HASH contra un indice unico: la clave en claro no existe
     * en la base, asi que no hay ninguna comparacion de secretos en memoria.
     *
     * @return la identidad del dispositivo si la clave es valida y esta activo;
     * vacio en cualquier otro caso (clave desconocida, revocado, cabecera ausente).
     */
    @Transactional(readOnly = true)
    public Optional<AuthenticatedDevice> verify(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }
        // El mapeo ocurre DENTRO de la transaccion: fuera de ella la relacion
        // LAZY con el paciente ya no se puede leer.
        return devices.findByApiKeyHashAndActiveTrue(ApiKeys.hash(apiKey))
                .map(d -> new AuthenticatedDevice(
                        d.getId(),
                        d.getPatient().getId(),
                        d.getPatient().getCode(),
                        d.getSilenceThresholdSeconds()));
    }

    /**
     * Revoca un dispositivo: deja de poder ingerir eventos de inmediato.
     *
     * No se borra la fila: el historial de eventos apunta a ella y los eventos son
     * inmutables. Un dispositivo revocado sigue existiendo para que su historial
     * siga siendo legible.
     */
    @Transactional
    public void revoke(UUID deviceId) {
        Device device = devices.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el dispositivo " + deviceId));

        if (!device.isActive()) {
            log.debug("Dispositivo {} ya estaba revocado", deviceId);
            return;
        }

        device.revoke(Instant.now());
        devices.save(device);
        log.warn("Dispositivo revocado | deviceId={} apiKeyPrefix={}",
                deviceId, device.getApiKeyPrefix());
    }
}
