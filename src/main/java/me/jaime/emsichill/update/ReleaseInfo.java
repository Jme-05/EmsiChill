package me.jaime.emsichill.update;

/** Versión publicada, página informativa y JAR instalable. */
public record ReleaseInfo(String tag, String pageUrl, String notes, ReleaseAsset asset) {
}
