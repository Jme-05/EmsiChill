package me.jaime.emsichill.update;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReleaseAssetTest {
    @Test
    void acceptsPositiveSizeAndSha256Digest() {
        ReleaseAsset asset = new ReleaseAsset("EmsiChill-5.1.5.jar", "https://github.com/Jme-05/EmsiChill",
            1024L, "sha256:" + "a".repeat(64));

        assertTrue(asset.hasVerifiedMetadata());
    }

    @Test
    void rejectsFeedAssetWithoutVerifiedMetadata() {
        ReleaseAsset asset = new ReleaseAsset("EmsiChill-5.1.5.jar", "https://github.com/Jme-05/EmsiChill",
            -1L, null);

        assertFalse(asset.hasVerifiedMetadata());
    }

    @Test
    void rejectsMalformedDigest() {
        ReleaseAsset asset = new ReleaseAsset("EmsiChill-5.1.5.jar", "https://github.com/Jme-05/EmsiChill",
            1024L, "sha256:not-a-real-digest");

        assertFalse(asset.hasVerifiedMetadata());
    }
}
