package me.jaime.emsichill.update;

/** Archivo descargable asociado a una Release de GitHub. */
public record ReleaseAsset(String name, String downloadUrl, long size, String digest) {
}
