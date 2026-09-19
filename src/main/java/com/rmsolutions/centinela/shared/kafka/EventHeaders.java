package com.rmsolutions.centinela.shared.kafka;

/**
 * Cabeceras propias que viajan con cada mensaje de Kafka.
 *
 * Vive en 'shared' porque es contrato entre quien publica (ingesta) y quien
 * consume (historial y alertas).
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
public final class EventHeaders {

    /**
     * Dispositivo que emitio el evento, ya autenticado por el webhook.
     *
     * Sin esta cabecera el consumidor tendria que adivinar el emisor a partir del
     * paciente, y con mas de un dispositivo activo no hay forma de acertar.
     */
    public static final String DEVICE_ID = "centinela-device-id";

    private EventHeaders() {
    }
}
