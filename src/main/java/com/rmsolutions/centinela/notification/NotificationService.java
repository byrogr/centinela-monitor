package com.rmsolutions.centinela.notification;

import com.rmsolutions.centinela.domain.OsdEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Punto de integracion con canales de emergencia.
 *
 * FASE 1 (ahora): solo registra en log lo que HARIA. Cero efectos externos.
 * FASE 3 (futuro): aqui se conectan canales reales, priorizando fiabilidad:
 *   1. Llamada de voz automatica (mas confiable que un push que se puede perder).
 *   2. SMS al/los cuidador(es).
 *   3. Push a la app del cuidador.
 * En produccion, este servicio debe ser IDEMPOTENTE (evitar notificar dos veces
 * el mismo evento tras un re-consumo) y con reintentos + dead-letter.
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
@Service
@Profile("!simulator")
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    public void dispatchEmergency(OsdEvent event) {
        log.error("[NOTIFY][EMERGENCIA] Se contactaria a los cuidadores AHORA "
                + "(llamada + SMS + push). Evento={}", event);
        // TODO Fase 3: Azure Communication Services (voz/SMS) o Twilio, + push (FCM/APNs).
    }

    public void dispatchWarning(OsdEvent event) {
        log.warn("[NOTIFY][ADVERTENCIA] Aviso no urgente a cuidadores. Evento={}", event);
        // TODO Fase 3: push/notificacion silenciosa; agrupar para evitar fatiga de alertas.
    }
}
