package me.jaime.emsichill.update;

/** Download metadata returned by PaperMC for a server jar. */
record PaperBuildDownload(String name, String url, long size, String sha256) {
    boolean hasVerifiedMetadata() {
        return this.name != null && this.name.endsWith(".jar")
            && this.url != null && this.url.startsWith("https://")
            && this.size > 0L
            && this.sha256 != null
            && this.sha256.matches("(?i)[0-9a-f]{64}");
    }
}
