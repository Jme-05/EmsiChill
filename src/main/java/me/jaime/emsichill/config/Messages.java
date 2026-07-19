package me.jaime.emsichill.config;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import me.jaime.emsichill.Main;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Resuelve mensajes por clave, aplica el prefijo y sustituye marcadores antes de enviarlos como
 * componentes Adventure.
 */
public final class Messages {
    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

    private final Main plugin;
    private final Set<String> warnedMissingKeys = ConcurrentHashMap.newKeySet();
    private ConfigFile messagesFile;
    private YamlConfiguration bundledFallback;
    private String prefix;

    public Messages(final Main plugin) {
        this.plugin = plugin;
        this.reload();
    }

    public void reload() {
        String language = this.plugin.settings().getString("language", "es").toLowerCase();
        if (!language.equals("en")) {
            language = "es";
        }
        String resourcePath = "messages_" + language + ".yml";
        File existingFile = new File(this.plugin.getDataFolder(), resourcePath);
        int existingTextRevision = existingFile.exists()
            ? YamlConfiguration.loadConfiguration(existingFile).getInt("_meta.text-revision", 0)
            : Integer.MAX_VALUE;
        this.messagesFile = new ConfigFile(this.plugin, resourcePath);
        try (InputStream stream = this.plugin.getResource(resourcePath)) {
            this.bundledFallback = stream == null ? new YamlConfiguration() : YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8)
            );
        } catch (java.io.IOException exception) {
            this.bundledFallback = new YamlConfiguration();
            this.plugin.getLogger().warning("No se pudieron cargar los mensajes incluidos en EmsiChill.");
        }
        // Las revisiones aplican correcciones importantes una vez y conservan cambios posteriores.
        int bundledTextRevision = this.bundledFallback.getInt("_meta.text-revision", 0);
        if (language.equals("es") && existingTextRevision < bundledTextRevision) {
            for (String key : this.bundledFallback.getKeys(true)) {
                if (this.bundledFallback.isString(key)) {
                    this.messagesFile.yaml().set(key, this.bundledFallback.getString(key));
                }
            }
            this.messagesFile.yaml().set("_meta.text-revision", bundledTextRevision);
            this.messagesFile.save();
        }
        boolean updated = false;
        for (String key : this.messagesFile.yaml().getKeys(true)) {
            Object value = this.messagesFile.yaml().get(key);
            if (!(value instanceof String text)) continue;
            String corrected = text
                .replace("Contrasenas", "Contraseñas")
                .replace("contrasenas", "contraseñas")
                .replace("Contrasena", "Contraseña")
                .replace("contrasena", "contraseña");
            if (!corrected.equals(text)) {
                this.messagesFile.yaml().set(key, corrected);
                updated = true;
            }
        }
        if (updated) this.messagesFile.save();
        this.prefix = this.plugin.settings().getString("prefix", "&8[&5EmsiChill&8] ");
    }

    public void send(final CommandSender sender, final String key, final String... replacements) {
        sender.sendMessage(this.component(key, replacements));
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
        String text = this.resolveText(key, replacements);
        int marker = text.indexOf("{url}");
        Component link = SERIALIZER.deserialize("&b&n" + url)
            .clickEvent(ClickEvent.openUrl(url))
            .hoverEvent(HoverEvent.showText(this.unprefixed("update.link-hover")));
        if (marker < 0) {
            sender.sendMessage(SERIALIZER.deserialize(this.prefix + text + " ").append(link));
            return;
        }

        Component message = SERIALIZER.deserialize(this.prefix + text.substring(0, marker))
            .append(link)
            .append(SERIALIZER.deserialize(text.substring(marker + "{url}".length())));
        sender.sendMessage(message);
    }

    public Component component(final String key, final String... replacements) {
        return SERIALIZER.deserialize(this.prefix + this.resolveText(key, replacements));
    }

    private String resolveText(final String key, final String... replacements) {
        String text = this.messagesFile.yaml().getString(key);
        if (text == null) text = this.bundledFallback.getString(key);
        // Una clave ausente usa el recurso incluido antes de mostrar un error genérico.
        if (text == null) {
            if (this.warnedMissingKeys.add(key)) {
                this.plugin.getLogger().warning("Falta la clave de mensaje: " + key);
            }
            text = "&cNo se pudo completar este comando. Usa &e/emsichill help&c.";
        }
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            text = text.replace(replacements[index], replacements[index + 1]);
        }
        return text;
    }

    public Component unprefixed(final String key, final String... replacements) {
        String text = this.messagesFile.yaml().getString(key);
        if (text == null) text = this.bundledFallback.getString(key, key);
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            text = text.replace(replacements[index], replacements[index + 1]);
        }
        return SERIALIZER.deserialize(text);
    }
}
