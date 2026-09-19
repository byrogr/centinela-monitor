package com.rmsolutions.centinela.history.domain;

import com.rmsolutions.centinela.shared.config.AppProperties;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Convierte el campo 'Time' de OSD en un instante absoluto.
 *
 * OSD lo envia como texto local sin zona ("2026-09-18 00:05:32"), asi que hay
 * que interpretarlo en la zona del celular. Esto NO es un detalle menor: un
 * desfase de horas haria que el vigilante de silencio calcule mal el tiempo sin
 * senal, o que un evento caiga en la particion mensual equivocada.
 *
 * Por eso la logica es explicita y con pruebas propias, en vez de un conversor
 * generado o un parseo suelto dentro del consumidor.
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
@Slf4j
@Component
public class OsdTimeParser {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ZoneId zona;

    public OsdTimeParser(AppProperties props) {
        this.zona = ZoneId.of(props.osdTimeZone());
        log.info("Los timestamps de OSD se interpretan en la zona {}", zona);
    }

    /**
     * @return el instante, o vacio si el texto no es interpretable. El llamador
     * decide que hacer: aqui no inventamos una fecha, porque un timestamp
     * fabricado contaminaria el historial y romperia la deduplicacion.
     */
    public Optional<Instant> aInstante(String osdTime) {
        if (osdTime == null || osdTime.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDateTime.parse(osdTime.trim(), FORMATO)
                    .atZone(zona)
                    .toInstant());
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    public ZoneId zona() {
        return zona;
    }
}
