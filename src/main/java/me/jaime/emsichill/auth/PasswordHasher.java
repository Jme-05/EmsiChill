package me.jaime.emsichill.auth;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Crea y verifica hashes PBKDF2. También genera un hash actualizado cuando una cuenta usa menos
 * iteraciones que la configuración actual.
 */
public final class PasswordHasher {
    private static final int HASH_BITS = 256;
    private static final int SALT_BYTES = 16;

    private final SecureRandom random;

    public PasswordHasher() {
        this(new SecureRandom());
    }

    PasswordHasher(final SecureRandom random) {
        this.random = random;
    }

    public PasswordRecord create(final String playerName, final char[] password, final int iterations)
        throws GeneralSecurityException {
        byte[] salt = new byte[SALT_BYTES];
        this.random.nextBytes(salt);
        byte[] hash = this.derive(password, salt, iterations);
        return new PasswordRecord(playerName, iterations,
            Base64.getEncoder().encodeToString(salt),
            Base64.getEncoder().encodeToString(hash));
    }

    public Verification verify(final PasswordRecord record, final char[] password, final int targetIterations)
        throws GeneralSecurityException {
        byte[] salt = Base64.getDecoder().decode(record.salt());
        byte[] expected = Base64.getDecoder().decode(record.hash());
        boolean matches = MessageDigest.isEqual(expected, this.derive(password, salt, record.iterations()));
        if (!matches || record.iterations() >= targetIterations) {
            return new Verification(matches, null);
        }
        return new Verification(true, this.create(record.name(), password, targetIterations));
    }

    private byte[] derive(final char[] password, final byte[] salt, final int iterations)
        throws GeneralSecurityException {
        PBEKeySpec specification = new PBEKeySpec(password, salt, iterations, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(specification)
                .getEncoded();
        } finally {
            specification.clearPassword();
        }
    }

    public record Verification(boolean matches, PasswordRecord upgradedRecord) {
    }
}