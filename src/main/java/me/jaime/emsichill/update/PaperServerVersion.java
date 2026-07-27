package me.jaime.emsichill.update;

import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Runtime Paper/Minecraft version detected from Bukkit strings. */
public record PaperServerVersion(String minecraftVersion, OptionalInt build) {
    private static final Pattern BUILD_PATTERN = Pattern.compile("(?i)\\b(?:paper|purpur|folia)[^\\n\\r]*?-(\\d+)\\b");
    private static final Pattern MC_PATTERN = Pattern.compile("(?i)\\bMC:\\s*([0-9]+(?:\\.[0-9]+)*(?:-[A-Za-z0-9._-]+)?)");

    static PaperServerVersion detect(final String minecraftVersion, final String serverVersion) {
        String detectedMinecraft = normalizeMinecraftVersion(minecraftVersion);
        if (detectedMinecraft.isBlank()) {
            Matcher matcher = MC_PATTERN.matcher(serverVersion == null ? "" : serverVersion);
            detectedMinecraft = matcher.find() ? matcher.group(1) : "unknown";
        }
        return new PaperServerVersion(detectedMinecraft, parseBuild(serverVersion));
    }

    public String label() {
        return this.build.isPresent()
            ? this.minecraftVersion + " build #" + this.build.getAsInt()
            : this.minecraftVersion;
    }

    private static OptionalInt parseBuild(final String serverVersion) {
        Matcher matcher = BUILD_PATTERN.matcher(serverVersion == null ? "" : serverVersion);
        return matcher.find() ? OptionalInt.of(Integer.parseInt(matcher.group(1))) : OptionalInt.empty();
    }

    private static String normalizeMinecraftVersion(final String value) {
        return value == null ? "" : value.trim();
    }
}
