package com.rmsolutions.centinela.registry.application;

import java.util.UUID;

/**
 * Resultado de registrar un dispositivo.
 *
 * <p>Es la UNICA vez que la API key en claro existe fuera del dispositivo. No se
 * persiste, no se registra en logs y no hay forma de volver a consultarla: si se
 * pierde, el camino es revocar el dispositivo y registrar uno nuevo.
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
public record RegisteredDevice(
        UUID deviceId,
        String label,
        String plainApiKey,
        String apiKeyPrefix
) {
    /**
     * Se sobreescribe para que la clave no se filtre si alguien registra este
     * objeto en un log o lo incluye en un mensaje de error.
     */
    @Override
    public String toString() {
        return "RegisteredDevice[deviceId=%s, label=%s, apiKeyPrefix=%s, plainApiKey=***]"
                .formatted(deviceId, label, apiKeyPrefix);
    }
}
