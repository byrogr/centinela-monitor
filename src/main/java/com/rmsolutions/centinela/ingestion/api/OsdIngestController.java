package com.rmsolutions.centinela.ingestion.api;

import com.rmsolutions.centinela.ingestion.application.EventIngestionService;
import com.rmsolutions.centinela.ingestion.application.IngestOutcome;
import com.rmsolutions.centinela.ingestion.domain.OsdEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Webhook HTTP. El celular con OSD hace POST del JSON aqui.
 *
 * <p>Adaptador de entrada: autentica y decide el codigo HTTP, nada mas. La logica
 * vive en {@link EventIngestionService}.
 *
 * <p>Cada dispositivo se identifica con su propia credencial en la cabecera
 * 'X-Device-Key'. Sustituye al secreto compartido de la Fase 1, que era el mismo
 * para todos, venia con un valor por defecto en el yml y ademas dejaba pasar
 * cualquier peticion si se configuraba vacio.
 *
 * <p>Se responde 202 rapido y el procesamiento sigue de forma asincrona: un webhook
 * que tarda hace que el dispositivo reintente.
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/osd")
@Profile("!simulator")
@RequiredArgsConstructor
public class OsdIngestController {

    private final EventIngestionService ingestion;

    @PostMapping("/events")
    public ResponseEntity<Map<String, Object>> ingest(
            @RequestHeader(value = "X-Device-Key", required = false) String deviceKey,
            @RequestBody OsdEvent event) {

        if (event == null) {
            return ResponseEntity.badRequest().build();
        }

        return switch (ingestion.ingest(deviceKey, event)) {
            case IngestOutcome.Accepted a -> ResponseEntity.accepted().body(Map.of(
                    "status", "accepted",
                    "severity", a.severity().name(),
                    "routedTo", a.topics()));

            // 202 y no un error: para el dispositivo el evento ya se acepto, y
            // devolverle un fallo solo lo haria reintentar en bucle.
            case IngestOutcome.Duplicate d -> ResponseEntity.accepted().body(Map.of(
                    "status", "duplicate"));

            case IngestOutcome.Unauthorized u ->
                    ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

            case IngestOutcome.RateLimited r ->
                    ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();

            case IngestOutcome.Unprocessable p -> ResponseEntity
                    .unprocessableEntity()
                    .body(Map.of("status", "rejected", "reason", p.reason()));
        };
    }
}
