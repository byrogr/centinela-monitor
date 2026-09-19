package com.rmsolutions.centinela.shared.domain;

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
 * <p>OSD lo envia como texto local sin zona ("2026-09-18 00:05:32"), asi que hay
 * que interpretarlo en la zona del celular. Esto NO es un detalle menor: un
 * desfase de horas haria que el vigilante de silencio calcule mal el tiempo sin
 * senal, o que un evento caiga en la particion mensual equivocada.
 *
 * <p>Por eso la logica es explicita y con pruebas propias, en vez de un conversor
 * generado o un parseo suelto dentro del consumidor.
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
@Slf4j
@Component
public class OsdTimeParser {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ZoneId zone;

    public OsdTimeParser(AppProperties props) {
        this.zone = ZoneId.of(props.osdTimeZone());
        log.info("Los timestamps de OSD se interpretan en la zona {}", zone);
    }

    /**
     * @return el instante, o vacio si el texto no es interpretable. El llamador
     * decide que hacer: aqui no inventamos una fecha, porque un timestamp
     * fabricado contaminaria el historial y romperia la deduplicacion.
     */
    public Optional<Instant> toInstant(String osdTime) {
        if (osdTime == null || osdTime.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDateTime.parse(osdTime.trim(), FORMAT)
                    .atZone(zone)
                    .toInstant());
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /**
     * Operacion inversa: expresa un instante en el formato de texto de OSD.
     *
     * <p>La usa el vigilante de silencio para fabricar su evento sintetico. Va aqui y
     * no suelta en el vigilante para que leer y escribir usen SIEMPRE la misma zona:
     * si se separaran, un evento sintetico quedaria desplazado respecto a los reales.
     */
    public String toOsdTime(Instant instant) {
        return FORMAT.format(instant.atZone(zone));
    }

    public ZoneId zone() {
        return zone;
    }
}
