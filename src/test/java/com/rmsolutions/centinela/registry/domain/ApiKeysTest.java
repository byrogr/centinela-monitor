package com.rmsolutions.centinela.registry.domain;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas de la generacion y el hash de API keys.
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
class ApiKeysTest {

    @Test
    void theHashMatchesTheKnownSha256Vector() {
        // Vector publico de SHA-256("abc"). Si alguien cambiara el algoritmo,
        // todas las claves emitidas dejarian de verificar en silencio.
        assertThat(ApiKeys.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void theHashIsLowercaseHexadecimal() {
        // Postgres calcula encode(sha256(...), 'hex'), que produce minusculas.
        // Si Java devolviera mayusculas, el hash sembrado no casaria con el calculado.
        assertThat(ApiKeys.hash(ApiKeys.generate()))
                .hasSize(64)
                .matches("[0-9a-f]{64}");
    }

    @Test
    void everyGeneratedKeyIsUnique() {
        Set<String> keys = new HashSet<>();
        IntStream.range(0, 1000).forEach(i -> keys.add(ApiKeys.generate()));

        assertThat(keys).as("una colision significaria dos dispositivos con la misma credencial")
                .hasSize(1000);
    }

    @Test
    void theKeyCarriesTheBrandPrefixAndEnoughEntropy() {
        String key = ApiKeys.generate();

        assertThat(key).startsWith(ApiKeys.PREFIX);
        // 32 bytes en Base64URL sin relleno son 43 caracteres.
        assertThat(key.substring(ApiKeys.PREFIX.length())).hasSize(43);
    }

    @Test
    void thePrefixDoesNotRevealTheKey() {
        String key = ApiKeys.generate();
        String prefix = ApiKeys.prefix(key);

        assertThat(prefix).hasSize(ApiKeys.PREFIX_LENGTH);
        assertThat(key).startsWith(prefix);
        assertThat(key.length() - prefix.length())
                .as("lo que queda oculto debe seguir siendo impracticable de adivinar")
                .isGreaterThan(30);
    }

    @Test
    void anEmptyKeyIsNotHashed() {
        assertThatThrownBy(() -> ApiKeys.hash(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ApiKeys.hash("  ")).isInstanceOf(IllegalArgumentException.class);
    }
}
