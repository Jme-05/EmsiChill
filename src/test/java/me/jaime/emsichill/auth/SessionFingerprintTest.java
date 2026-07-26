package me.jaime.emsichill.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class SessionFingerprintTest {
    @Test
    void signsAddressDeterministicallyWithSameSecret() {
        String secret = SessionFingerprint.generateSecret();

        assertEquals(
            SessionFingerprint.addressSignature(secret, "203.0.113.10"),
            SessionFingerprint.addressSignature(secret, "203.0.113.10")
        );
    }

    @Test
    void changesSignatureWhenSecretOrAddressChanges() {
        String firstSecret = SessionFingerprint.generateSecret();
        String secondSecret = SessionFingerprint.generateSecret();
        String signature = SessionFingerprint.addressSignature(firstSecret, "203.0.113.10");

        assertNotEquals(signature, SessionFingerprint.addressSignature(secondSecret, "203.0.113.10"));
        assertNotEquals(signature, SessionFingerprint.addressSignature(firstSecret, "203.0.113.11"));
    }

    @Test
    void generatedSecretIsNotBlank() {
        assertFalse(SessionFingerprint.generateSecret().isBlank());
    }
}
