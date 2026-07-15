package me.jaime.emsichill.skin;

import java.time.Duration;
import java.util.List;

import org.bukkit.configuration.file.YamlConfiguration;

/** Valores ya validados que controlan consultas, caché, historial y skins aleatorias. */
public record SkinSettings(
    Duration requestTimeout,
    long cacheLifetimeSeconds,
    long cooldownSeconds,
    int favoriteLimit,
    int historyLimit,
    boolean ignoreConsecutiveHistoryDuplicates,
    boolean usePublicCatalog,
    int randomAttempts,
    long retryBaseSeconds,
    long retryMaximumSeconds,
    List<String> fallbackPremiumNames
) {
    public SkinSettings {
        fallbackPremiumNames = List.copyOf(fallbackPremiumNames);
    }

    public static SkinSettings from(final YamlConfiguration config) {
        long timeoutSeconds = Math.max(2L, config.getLong("settings.mojang-request-timeout-seconds", 10L));
        long cacheHours = Math.max(1L, config.getLong("settings.cache-duration-hours", 24L));
        long retryBase = Math.max(5L, config.getLong("random.provider-retry-base-seconds", 15L));

        return new SkinSettings(
            Duration.ofSeconds(timeoutSeconds),
            cacheHours * 3600L,
            Math.max(0L, config.getLong("settings.cooldown-seconds", 10L)),
            Math.max(1, config.getInt("favorites.maximum", 18)),
            Math.max(1, config.getInt("history.maximum-entries", 20)),
            config.getBoolean("history.ignore-consecutive-duplicates", true),
            config.getBoolean("random.use-public-catalog", true),
            Math.max(1, config.getInt("random.maximum-attempts", 6)),
            retryBase,
            Math.max(retryBase, config.getLong("random.provider-retry-maximum-seconds", 300L)),
            config.getStringList("random.fallback-premium-names")
        );
    }

    public static boolean isValidMinecraftName(final String value) {
        if (value == null || value.length() < 3 || value.length() > 16) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!Character.isLetterOrDigit(character) && character != '_') {
                return false;
            }
        }
        return true;
    }
}