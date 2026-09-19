package com.rmsolutions.centinela.history.domain;

import com.rmsolutions.centinela.shared.config.AppProperties;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * OSD manda la hora como texto local sin zona. Interpretarla mal desplaza todo
 * el historial y descuadra el calculo del vigilante de silencio, asi que la
 * conversion tiene pruebas propias.
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
class OsdTimeParserTest {

    private OsdTimeParser parserEn(String zona) {
        return new OsdTimeParser(new AppProperties("child-001", "token", 15, zona));
    }

    @Test
    void interpretaLaHoraLocalEnLaZonaConfigurada() {
        // Lima es UTC-5, asi que medianoche local son las 05:00 UTC.
        assertThat(parserEn("America/Lima").aInstante("2026-09-18 00:05:32"))
                .contains(Instant.parse("2026-09-18T05:05:32Z"));
    }

    @Test
    void laMismaHoraEnOtraZonaDaOtroInstante() {
        assertThat(parserEn("UTC").aInstante("2026-09-18 00:05:32"))
                .as("si la zona esta mal configurada el evento se guarda desplazado")
                .contains(Instant.parse("2026-09-18T00:05:32Z"));
    }

    @Test
    void noInventaUnaFechaCuandoElTextoNoEsInterpretable() {
        OsdTimeParser parser = parserEn("America/Lima");

        assertThat(parser.aInstante("no es una fecha")).isEmpty();
        assertThat(parser.aInstante("2026-13-45 99:99:99")).isEmpty();
        assertThat(parser.aInstante("")).isEmpty();
        assertThat(parser.aInstante(null)).isEmpty();
    }

    @Test
    void toleraEspaciosAlrededor() {
        assertThat(parserEn("America/Lima").aInstante("  2026-09-18 00:05:32  "))
                .contains(Instant.parse("2026-09-18T05:05:32Z"));
    }
}
