package com.rmsolutions.centinela.registry.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Generacion y hash de las API keys de dispositivo.
 *
 * <p>Decisiones:
 *
 *  <p>1. SHA-256 en hexadecimal minusculas, NO bcrypt/argon2. Esas funciones existen
 *     para contrasenas elegidas por personas, que tienen poca entropia y hay que
 *     encarecer a fuerza de iteraciones. Aqui la clave la genera el sistema con 256
 *     bits de aleatoriedad criptografica: no hay diccionario que atacar. Y el
 *     webhook esta en la ruta critica de una alerta de emergencia, asi que un hash
 *     deliberadamente lento en cada peticion costaria latencia sin aportar nada.
 *
 *  <p>2. El esquema debe coincidir EXACTAMENTE con el de la migracion V4, que calcula
 *     el hash con encode(sha256(...), 'hex') de PostgreSQL. Hay una prueba que lo
 *     verifica contra la clave sembrada.
 *
 *  <p>3. La clave en claro no se guarda en ningun sitio ni se registra en ningun log:
 *     se devuelve una unica vez al emitirla.
 *
 * @author Roger Rojas
 * @since 2026-09-18
 */
public final class ApiKeys {

    /** Marca visible de la clave. Lenzo es el nombre de cara al usuario. */
    public static final String PREFIX = "lenzo_";

    /** Cuantos caracteres se guardan en claro para identificar la clave sin revelarla. */
    public static final int PREFIX_LENGTH = 12;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int ENTROPY_BYTES = 32;

    private ApiKeys() {
    }

    /**
     * Genera una API key nueva. El llamador es responsable de entregarla al
     * dispositivo y olvidarla: no hay forma de recuperarla despues.
     */
    public static String generate() {
        byte[] bytes = new byte[ENTROPY_BYTES];
        RANDOM.nextBytes(bytes);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 en hexadecimal minusculas del texto UTF-8. Es lo unico que toca la base. */
    public static String hash(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("La API key no puede estar vacia");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(apiKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 es obligatorio en toda JVM; si falta, algo esta muy roto.
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", e);
        }
    }

    /**
     * Fragmento identificable de la clave, para mostrarla en el backoffice.
     * Son 12 caracteres de una clave de 256 bits: no permite reconstruirla.
     */
    public static String prefix(String apiKey) {
        return apiKey.length() <= PREFIX_LENGTH
                ? apiKey
                : apiKey.substring(0, PREFIX_LENGTH);
    }
}
