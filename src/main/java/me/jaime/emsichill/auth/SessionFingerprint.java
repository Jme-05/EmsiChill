package me.jaime.emsichill.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Genera una firma privada de la direccion de conexion sin guardar la direccion real. */
final class SessionFingerprint {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int SECRET_BYTES = 32;

    private SessionFingerprint() {
    }

    static String generateSecret() {
        byte[] secret = new byte[SECRET_BYTES];
        new SecureRandom().nextBytes(secret);
        return Base64.getEncoder().encodeToString(secret);
    }

    static String addressSignature(final String secret, final String address) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Base64.getEncoder().encodeToString(mac.doFinal(address.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not sign the authentication session", exception);
        }
    }
}
