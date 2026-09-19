package com.rmsolutions.centinela.shared.domain;

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

    private OsdTimeParser parserIn(String zone) {
        return new OsdTimeParser(new AppProperties(15, zone));
    }

    @Test
    void itReadsLocalTimeInTheConfiguredZone() {
        // Lima es UTC-5, asi que medianoche local son las 05:00 UTC.
        assertThat(parserIn("America/Lima").toInstant("2026-09-18 00:05:32"))
                .contains(Instant.parse("2026-09-18T05:05:32Z"));
    }

    @Test
    void theSameLocalTimeInAnotherZoneYieldsAnotherInstant() {
        assertThat(parserIn("UTC").toInstant("2026-09-18 00:05:32"))
                .as("si la zone esta mal configurada el evento se guarda desplazado")
                .contains(Instant.parse("2026-09-18T00:05:32Z"));
    }

    @Test
    void itDoesNotInventADateWhenTheTextIsUnparseable() {
        OsdTimeParser parser = parserIn("America/Lima");

        assertThat(parser.toInstant("no es una fecha")).isEmpty();
        assertThat(parser.toInstant("2026-13-45 99:99:99")).isEmpty();
        assertThat(parser.toInstant("")).isEmpty();
        assertThat(parser.toInstant(null)).isEmpty();
    }

    @Test
    void itToleratesSurroundingWhitespace() {
        assertThat(parserIn("America/Lima").toInstant("  2026-09-18 00:05:32  "))
                .contains(Instant.parse("2026-09-18T05:05:32Z"));
    }
}
