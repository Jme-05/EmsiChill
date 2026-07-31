package me.jaime.emsichill.config;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import me.jaime.emsichill.Main;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Resolves localized messages, applies the prefix and sends Adventure components.
 */
public final class Messages {
    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

    private final Main plugin;
    private final Set<String> warnedMissingKeys = ConcurrentHashMap.newKeySet();
    private final Map<String, ConfigFile> messagesFiles = new ConcurrentHashMap<>();
    private final Map<String, YamlConfiguration> bundledFallbacks = new ConcurrentHashMap<>();
    private ConfigFile playerLanguagesFile;
    private String prefix;

    public Messages(final Main plugin) {
        this.plugin = plugin;
        this.reload();
    }

    public void reload() {
        if (this.playerLanguagesFile == null) this.playerLanguagesFile = new ConfigFile(this.plugin, "player-languages.yml");
        else this.playerLanguagesFile.reload();
        this.loadLanguage("en");
        this.loadLanguage("es");
        this.prefix = this.plugin.settings().getString("prefix", "&8[&5EmsiChill&8] ");
    }

    private void loadLanguage(final String language) {
        String resourcePath = "messages_" + language + ".yml";
        File existingFile = new File(this.plugin.getDataFolder(), resourcePath);
        int existingTextRevision = existingFile.exists()
            ? YamlConfiguration.loadConfiguration(existingFile).getInt("_meta.text-revision", 0)
            : Integer.MAX_VALUE;
        ConfigFile messagesFile = new ConfigFile(this.plugin, resourcePath);
        YamlConfiguration bundledFallback;
        try (InputStream stream = this.plugin.getResource(resourcePath)) {
            bundledFallback = stream == null ? new YamlConfiguration() : YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8)
            );
        } catch (java.io.IOException exception) {
            bundledFallback = new YamlConfiguration();
            this.plugin.getLogger().warning("Could not load bundled EmsiChill messages for " + language + ".");
        }

        int bundledTextRevision = bundledFallback.getInt("_meta.text-revision", 0);
        if (existingTextRevision < bundledTextRevision) {
            for (String key : bundledFallback.getKeys(true)) {
                if (bundledFallback.isString(key)) {
                    messagesFile.yaml().set(key, bundledFallback.getString(key));
                }
            }
            messagesFile.yaml().set("_meta.text-revision", bundledTextRevision);
            messagesFile.save();
        }

        this.messagesFiles.put(language, messagesFile);
        this.bundledFallbacks.put(language, bundledFallback);
    }

    public boolean setPlayerLanguage(final Player player, final String language) {
        String normalized = normalizeLanguage(language);
        if (normalized == null) return false;
        String path = "players." + player.getUniqueId();
        this.playerLanguagesFile.yaml().set(path + ".name", player.getName());
        this.playerLanguagesFile.yaml().set(path + ".language", normalized);
        return this.playerLanguagesFile.save();
    }

    public String language(final CommandSender sender) {
        if (sender instanceof Player player && this.playerLanguagesFile != null) {
            String configured = this.playerLanguagesFile.yaml().getString("players." + player.getUniqueId() + ".language");
            String normalized = normalizeLanguage(configured);
            if (normalized != null) return normalized;
        }
        return this.defaultLanguage();
    }

    private String defaultLanguage() {
        String normalized = normalizeLanguage(this.plugin.settings().getString("language", "en"));
        return normalized == null ? "en" : normalized;
    }

    private static String normalizeLanguage(final String value) {
        if (value == null) return null;
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "en", "english" -> "en";
            case "es", "spanish", "espanol", "español" -> "es";
            default -> null;
        };
    }

    public void send(final CommandSender sender, final String key, final String... replacements) {
        sender.sendMessage(this.component(sender, key, replacements));
    }

    public void sendText(final CommandSender sender, final String text) {
        sender.sendMessage(SERIALIZER.deserialize(this.prefix + text));
    }

    public void sendLink(
        final CommandSender sender,
        final String key,
        final String url,
        final String... replacements
    ) {
        String text = this.resolveText(sender, key, replacements);
        int marker = text.indexOf("{url}");
        Component link = SERIALIZER.deserialize("&b&n" + url)
            .clickEvent(ClickEvent.openUrl(url))
            .hoverEvent(HoverEvent.showText(this.unprefixed(sender, "update.link-hover")));
        if (marker < 0) {
            sender.sendMessage(SERIALIZER.deserialize(this.prefix + text + " ").append(link));
            return;
        }

        Component message = SERIALIZER.deserialize(this.prefix + text.substring(0, marker))
            .append(link)
            .append(SERIALIZER.deserialize(text.substring(marker + "{url}".length())));
        sender.sendMessage(message);
    }

    public void sendUpdateActions(final Player player, final String version) {
        if (!player.hasPermission("emsichill.admin.update")) return;
        Component changes = this.unprefixed(player, "update.changes-button")
            .clickEvent(ClickEvent.runCommand("/emsichill update changes " + version))
            .hoverEvent(HoverEvent.showText(this.unprefixed(player, "update.changes-hover")));
        Component install = this.unprefixed(player, "update.install-button")
            .clickEvent(ClickEvent.runCommand("/emsichill update install " + version))
            .hoverEvent(HoverEvent.showText(this.unprefixed(player, "update.install-hover")));
        Component ignore = this.unprefixed(player, "update.ignore-button")
            .clickEvent(ClickEvent.runCommand("/emsichill update ignore " + version))
            .hoverEvent(HoverEvent.showText(this.unprefixed(player, "update.ignore-hover")));
        player.sendMessage(Component.text("  ").append(changes).append(Component.text("  "))
            .append(install).append(Component.text("  ")).append(ignore));
    }

    public void sendPaperUpdateActions(final Player player, final String version, final int build) {
        if (!player.hasPermission("emsichill.admin.update")) return;
        Component download = this.unprefixed(player, "update.paper-download-button")
            .clickEvent(ClickEvent.runCommand("/emsichill update paper download " + version + " " + build))
            .hoverEvent(HoverEvent.showText(this.unprefixed(player, "update.paper-download-hover")));
        Component ignore = this.unprefixed(player, "update.paper-ignore-button")
            .clickEvent(ClickEvent.runCommand("/emsichill update paper ignore " + version + " " + build))
            .hoverEvent(HoverEvent.showText(this.unprefixed(player, "update.paper-ignore-hover")));
        player.sendMessage(Component.text("  ").append(download).append(Component.text("  ")).append(ignore));
    }

    public void sendTeleportRequestActions(final Player player) {
        Component accept = this.unprefixed(player, "teleport.accept-button")
            .clickEvent(ClickEvent.runCommand("/tpaccept"))
            .hoverEvent(HoverEvent.showText(this.unprefixed(player, "teleport.accept-hover")));
        Component deny = this.unprefixed(player, "teleport.deny-button")
            .clickEvent(ClickEvent.runCommand("/tpdeny"))
            .hoverEvent(HoverEvent.showText(this.unprefixed(player, "teleport.deny-hover")));
        player.sendMessage(Component.text("  ").append(accept).append(Component.text("  ")).append(deny));
    }

    /** Sends external release-note text literally to avoid injected colors or click actions. */
    public void sendReleaseLine(final CommandSender sender, final String line) {
        sender.sendMessage(SERIALIZER.deserialize(this.prefix + "&7- &f").append(Component.text(line)));
    }

    public Component component(final String key, final String... replacements) {
        return SERIALIZER.deserialize(this.prefix + this.resolveText(this.defaultLanguage(), key, replacements));
    }

    public Component component(final CommandSender sender, final String key, final String... replacements) {
        return SERIALIZER.deserialize(this.prefix + this.resolveText(sender, key, replacements));
    }

    private String resolveText(final CommandSender sender, final String key, final String... replacements) {
        return this.resolveText(this.language(sender), key, replacements);
    }

    private String resolveText(final String language, final String key, final String... replacements) {
        ConfigFile messagesFile = this.messagesFiles.getOrDefault(language, this.messagesFiles.get("en"));
        YamlConfiguration bundledFallback = this.bundledFallbacks.getOrDefault(language, this.bundledFallbacks.get("en"));
        String text = messagesFile == null ? null : messagesFile.yaml().getString(key);
        if (text == null && bundledFallback != null) text = bundledFallback.getString(key);
        if (text == null && !"en".equals(language)) text = this.resolveText("en", key);
        if (text == null) {
            if (this.warnedMissingKeys.add(language + ":" + key)) {
                this.plugin.getLogger().warning("Missing message key: " + key + " (" + language + ")");
            }
            text = "&cThis command could not be completed. Use &e/emsichill help&c.";
        }
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            text = text.replace(replacements[index], replacements[index + 1]);
        }
        return text;
    }

    public Component unprefixed(final String key, final String... replacements) {
        return SERIALIZER.deserialize(this.resolveText(this.defaultLanguage(), key, replacements));
    }

    public Component unprefixed(final CommandSender sender, final String key, final String... replacements) {
        return SERIALIZER.deserialize(this.resolveText(sender, key, replacements));
    }

    /** Returns configurable text without color codes for embedding in other messages. */
    public String plain(final String key, final String... replacements) {
        return this.plain(this.defaultLanguage(), key, replacements);
    }

    public String plain(final CommandSender sender, final String key, final String... replacements) {
        return this.plain(this.language(sender), key, replacements);
    }

    private String plain(final String language, final String key, final String... replacements) {
        return this.resolveText(language, key, replacements).replaceAll("&[0-9A-FK-ORa-fk-or]", "");
    }
}
