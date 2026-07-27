package me.jaime.emsichill.update;

/** Latest Paper build that can be downloaded safely after admin confirmation. */
public record PaperBuildInfo(
    String project,
    String minecraftVersion,
    int build,
    String channel,
    PaperBuildDownload download
) {
    public String identifier() {
        return this.minecraftVersion + "#" + this.build;
    }

    public String pageUrl() {
        return "https://papermc.io/downloads/" + this.project;
    }
}
