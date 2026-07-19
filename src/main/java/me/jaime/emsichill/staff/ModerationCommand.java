package me.jaime.emsichill.staff;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import me.jaime.emsichill.Main;
import me.jaime.emsichill.util.CommandSuggestions;

/** Valida los comandos de sancion y delega el estado persistente al servicio de moderacion. */
public final class ModerationCommand implements CommandExecutor, TabCompleter {
    private static final List<String> MUTE_DURATIONS = List.of("30s", "5m", "30m", "1h", "1d");
    private static final int HISTORY_PAGE_SIZE = 10;
    private static final int MAX_WARNING_LENGTH = 200;
    private static final DateTimeFormatter HISTORY_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault());

    private final Main plugin;
    private final ModerationService moderation;

    public ModerationCommand(final Main plugin, final ModerationService moderation) {
        this.plugin = plugin;
        this.moderation = moderation;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (!this.plugin.moduleEnabled("staff")) {
            this.plugin.messages().send(sender, "general.module-disabled");
            return true;
        }
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "mute" -> this.mute(sender, args);
            case "unmute" -> this.unmute(sender, args);
            case "warn" -> this.warn(sender, args);
            case "warnings" -> this.warnings(sender, args);
            default -> true;
        };
    }

    private boolean mute(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("emsichill.mute")) return this.noPermission(sender);
        if (args.length < 1 || args.length > 2) {
            this.plugin.messages().send(sender, "staff.mute-usage");
            return true;
        }
        String targetName = this.findKnownName(args[0]);
        if (targetName == null) return this.unknownPlayer(sender);

        long duration = 0L;
        if (args.length == 2) {
            OptionalLong parsed = MuteDuration.parse(args[1]);
            if (parsed.isEmpty()) {
                this.plugin.messages().send(sender, "staff.mute-duration-invalid");
                return true;
            }
            duration = parsed.getAsLong();
        }
        MuteRecord mute = this.moderation.mute(targetName, sender.getName(), duration);
        String displayedDuration = mute.permanent()
            ? this.plugin.messages().plain("staff.duration-permanent")
            : MuteDuration.format(duration);
        this.plugin.messages().send(sender, "staff.mute-success", "{player}", targetName,
            "{duration}", displayedDuration);
        Player onlineTarget = this.findOnline(targetName);
        if (onlineTarget != null) {
            this.plugin.messages().send(onlineTarget, "staff.muted", "{duration}", displayedDuration);
        }
        this.plugin.audit().log("PLAYER_MUTE", "actor=" + sender.getName() + " target=" + targetName
            + " duration=" + displayedDuration);
        return true;
    }

    private boolean unmute(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("emsichill.unmute")) return this.noPermission(sender);
        if (args.length != 1) {
            this.plugin.messages().send(sender, "staff.unmute-usage");
            return true;
        }
        String targetName = this.findKnownName(args[0]);
        if (targetName == null) return this.unknownPlayer(sender);
        if (!this.moderation.unmute(targetName, sender.getName())) {
            this.plugin.messages().send(sender, "staff.not-muted", "{player}", targetName);
            return true;
        }
        this.plugin.messages().send(sender, "staff.unmute-success", "{player}", targetName);
        Player onlineTarget = this.findOnline(targetName);
        if (onlineTarget != null) this.plugin.messages().send(onlineTarget, "staff.unmuted");
        this.plugin.audit().log("PLAYER_UNMUTE", "actor=" + sender.getName() + " target=" + targetName);
        return true;
    }

    private boolean warn(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("emsichill.warn")) return this.noPermission(sender);
        if (args.length < 2) {
            this.plugin.messages().send(sender, "staff.warn-usage");
            return true;
        }
        String targetName = this.findKnownName(args[0]);
        if (targetName == null) return this.unknownPlayer(sender);
        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        if (reason.isBlank() || reason.length() > MAX_WARNING_LENGTH) {
            this.plugin.messages().send(sender, "staff.warn-reason-invalid");
            return true;
        }
        this.moderation.warn(targetName, sender.getName(), reason);
        this.plugin.messages().send(sender, "staff.warn-success", "{player}", targetName, "{reason}", reason);
        Player onlineTarget = this.findOnline(targetName);
        if (onlineTarget != null) {
            this.plugin.messages().send(onlineTarget, "staff.warned", "{moderator}", sender.getName(),
                "{reason}", reason);
        }
        this.plugin.audit().log("PLAYER_WARNING", "actor=" + sender.getName() + " target=" + targetName
            + " reason=" + reason);
        return true;
    }

    private boolean warnings(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("emsichill.warnings")) return this.noPermission(sender);
        if (args.length != 1) {
            this.plugin.messages().send(sender, "staff.warnings-usage");
            return true;
        }
        String targetName = this.findKnownName(args[0]);
        if (targetName == null) return this.unknownPlayer(sender);
        this.moderation.activeMute(targetName);
        List<ModerationEntry> history = this.moderation.history(targetName);
        if (history.isEmpty()) {
            this.plugin.messages().send(sender, "staff.history-empty", "{player}", targetName);
            return true;
        }
        this.plugin.messages().send(sender, "staff.history-header", "{player}", targetName,
            "{count}", Integer.toString(history.size()));
        for (ModerationEntry entry : history.subList(0, Math.min(HISTORY_PAGE_SIZE, history.size()))) {
            String key = switch (entry.action()) {
                case MUTE -> "staff.history-entry-mute";
                case UNMUTE -> "staff.history-entry-unmute";
                case WARNING -> "staff.history-entry-warning";
            };
            this.plugin.messages().send(sender, key,
                "{date}", HISTORY_DATE.format(Instant.ofEpochMilli(entry.createdAt())),
                "{moderator}", entry.moderator(), "{reason}", entry.reason());
        }
        if (history.size() > HISTORY_PAGE_SIZE) {
            this.plugin.messages().send(sender, "staff.history-truncated",
                "{shown}", Integer.toString(HISTORY_PAGE_SIZE), "{total}", Integer.toString(history.size()));
        }
        return true;
    }

    private String findKnownName(final String requested) {
        Player online = this.findOnline(requested);
        if (online != null) return online.getName();
        String stored = this.moderation.knownName(requested);
        if (stored != null) return stored;
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            String name = player.getName();
            if (name != null && name.equalsIgnoreCase(requested)) return name;
        }
        return null;
    }

    private Player findOnline(final String name) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().equalsIgnoreCase(name)) return player;
        }
        return null;
    }

    private boolean unknownPlayer(final CommandSender sender) {
        this.plugin.messages().send(sender, "staff.player-unknown");
        return true;
    }

    private boolean noPermission(final CommandSender sender) {
        this.plugin.messages().send(sender, "general.no-permission");
        return true;
    }

    @Override
    public List<String> onTabComplete(
        final CommandSender sender,
        final Command command,
        final String alias,
        final String[] args
    ) {
        if (command.getName().equalsIgnoreCase("mute") && args.length == 2) {
            return CommandSuggestions.filter(MUTE_DURATIONS, args[1]);
        }
        if (args.length != 1) return Collections.emptyList();
        List<String> names = new ArrayList<>();
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            if (player.getName() != null) names.add(player.getName());
        }
        return CommandSuggestions.filter(names, args[0]);
    }
}
