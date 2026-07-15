package me.jaime.emsichill.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class PasswordHasherTest {
    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void verifiesTheOriginalPassword() throws Exception {
        PasswordRecord record = this.hasher.create("Jaime", "secreto".toCharArray(), 50_000);

        PasswordHasher.Verification result = this.hasher.verify(record, "secreto".toCharArray(), 50_000);

        assertTrue(result.matches());
        assertNull(result.upgradedRecord());
    }

    @Test
    void rejectsAnIncorrectPassword() throws Exception {
        PasswordRecord record = this.hasher.create("Jaime", "secreto".toCharArray(), 50_000);

        PasswordHasher.Verification result = this.hasher.verify(record, "incorrecta".toCharArray(), 50_000);

        assertFalse(result.matches());
        assertNull(result.upgradedRecord());
    }

    @Test
    void upgradesOldHashesAfterSuccessfulVerification() throws Exception {
        PasswordRecord record = this.hasher.create("Jaime", "secreto".toCharArray(), 50_000);

        PasswordHasher.Verification result = this.hasher.verify(record, "secreto".toCharArray(), 60_000);

        assertTrue(result.matches());
        assertNotNull(result.upgradedRecord());
        assertTrue(result.upgradedRecord().iterations() == 60_000);
    }
}
