package me.jaime.emsichill.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VersionNumberTest {
    @Test
    void comparesReleaseTagsNumerically() {
        VersionNumber installed = VersionNumber.parse("5.0.9").orElseThrow();
        VersionNumber latest = VersionNumber.parse("v5.1.0").orElseThrow();

        assertTrue(latest.compareTo(installed) > 0);
    }

    @Test
    void treatsMissingPartsAsZero() {
        VersionNumber shortVersion = VersionNumber.parse("v5.1").orElseThrow();
        VersionNumber fullVersion = VersionNumber.parse("5.1.0").orElseThrow();

        assertEquals(0, shortVersion.compareTo(fullVersion));
    }

    @Test
    void rejectsNonNumericVersions() {
        assertTrue(VersionNumber.parse("latest").isEmpty());
    }
}
