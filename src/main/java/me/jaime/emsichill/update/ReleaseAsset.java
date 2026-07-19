package me.jaime.emsichill.update;

/** Archivo descargable asociado a una Release de GitHub. */
public record ReleaseAsset(String name, String downloadUrl, long size, String digest) {
    boolean hasVerifiedMetadata() {
        return this.size > 0L && this.digest != null && this.digest.matches("(?i)sha256:[0-9a-f]{64}");
    }
}
