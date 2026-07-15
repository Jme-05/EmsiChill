package me.jaime.emsichill.commands;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import me.jaime.emsichill.Main;
import me.jaime.emsichill.auth.AuthenticationManager;
import me.jaime.emsichill.config.Messages;
import me.jaime.emsichill.grave.GraveManager;
import me.jaime.emsichill.home.HomeManager;
import me.jaime.emsichill.maintenance.MaintenanceService;
import me.jaime.emsichill.playerinfo.PlayerInfoManager;
import me.jaime.emsichill.region.RegionManager;
import me.jaime.emsichill.staff.StaffManager;
import me.jaime.emsichill.storage.DataStore;
import me.jaime.emsichill.teleport.TeleportManager;
import me.jaime.emsichill.util.AuditLogger;
import me.jaime.emsichill.util.CommandSuggestions;

/**
 * Implementa el comando administrativo principal: ayuda, recarga, diagnóstico, respaldos y
 * ajustes globales que afectan a varios módulos.
 */
public final class EmsiChillCommand implements CommandExecutor, TabCompleter {
    private final Main plugin;
    private final Messages messages;
    private final AuditLogger audit;
    private final DataStore dataStore;
    private final MaintenanceService maintenance;
    private final AuthenticationManager authentication;
    private final SkinCommand skins;
    private final TeleportManager teleports;
    private final HomeManager homes;
    private final PlayerInfoManager playerInfo;
    private final StaffManager staff;
    private final RegionManager regions;
    private final GraveManager graves;

    public EmsiChillCommand(
        final Main plugin,
        final MaintenanceService maintenance,
        final AuthenticationManager authentication,
        final SkinCommand skins,
        final TeleportManager teleports,
        final HomeManager homes,
        final PlayerInfoManager playerInfo,
        final StaffManager staff,
        final RegionManager regions,
        final GraveManager graves
    ) {
        this.plugin = plugin;
        this.messages = plugin.messages();
        this.audit = plugin.audit();
        this.dataStore = plugin.dataStore();
        this.maintenance = maintenance;
        this.authentication = authentication;
        this.skins = skins;
        this.teleports = teleports;
        this.homes = homes;
        this.playerInfo = playerInfo;
        this.staff = staff;
        this.regions = regions;
        this.graves = graves;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (args.length == 3 && args[0].equalsIgnoreCase("rtp") && args[1].equalsIgnoreCase("cooldown")) {
            return this.changeRtpCooldown(sender, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("homes") && args[1].equalsIgnoreCase("limit")) {
            return this.changeHomeLimit(sender, args[2]);
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            return this.reload(sender);
        }
        if (args.length == 1 && List.of("status", "doctor", "backup", "migrate")
            .contains(args[0].toLowerCase(Locale.ROOT))) {
            if (!sender.hasPermission("emsichill.admin.maintenance")) {
                this.messages.send(sender, "general.no-permission");
                return true;
            }
            return this.handleMaintenance(sender, args[0].toLowerCase(Locale.ROOT));
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("help")) {
            this.sendHelp(sender, args.length >= 2 ? args[1] : "categories");
            return true;
        }
        this.sendHelp(sender, "principal");
        return true;
    }

    private boolean changeRtpCooldown(final CommandSender sender, final String value) {
        if (!sender.hasPermission("emsichill.rtp.admin")) {
            this.messages.send(sender, "general.no-permission");
            return true;
        }
        try {
            long minutes = Long.parseLong(value);
            if (this.teleports.setRtpCooldownMinutes(sender, minutes)) {
                this.audit.log("RTP_COOLDOWN_CHANGE", "actor=" + sender.getName() + " minutes=" + minutes);
            }
        } catch (NumberFormatException exception) {
            this.messages.send(sender, "teleport.rtp-cooldown-invalid");
        }
        return true;
    }

    private boolean changeHomeLimit(final CommandSender sender, final String value) {
        if (!sender.hasPermission("emsichill.homes.admin")) {
            this.messages.send(sender, "general.no-permission");
            return true;
        }
        try {
            this.homes.setDefaultHomeLimit(sender, Integer.parseInt(value));
        } catch (NumberFormatException exception) {
            this.messages.send(sender, "teleport.home-limit-invalid");
        }
        return true;
    }

    private boolean reload(final CommandSender sender) {
        if (!sender.hasPermission("emsichill.admin.reload")) {
            this.messages.send(sender, "general.no-permission");
            return true;
        }
        this.plugin.reloadPlugin();
        this.audit.log("PLUGIN_RELOAD", "actor=" + sender.getName());
        this.messages.send(sender, "general.reloaded");
        return true;
    }

    private boolean handleMaintenance(final CommandSender sender, final String action) {
        switch (action) {
            case "status" -> this.sendStatus(sender);
            case "doctor" -> this.runDoctor(sender);
            case "backup" -> this.createBackup(sender);
            case "migrate" -> this.persistData(sender);
            default -> { }
        }
        return true;
    }

    private void sendStatus(final CommandSender sender) {
        this.messages.send(sender, "maintenance.status-header");
        this.messages.send(sender, "maintenance.status-players",
            "{registered}", Integer.toString(this.authentication.registeredCount()),
            "{authenticated}", Integer.toString(this.authentication.authenticatedCount()),
            "{tracked}", Integer.toString(this.playerInfo.trackedPlayerCount()));
        this.messages.send(sender, "maintenance.status-world",
            "{homes}", Integer.toString(this.homes.homeCount()),
            "{home-players}", Integer.toString(this.homes.playerCount()),
            "{regions}", Integer.toString(this.regions.regionCount()),
            "{chunks}", Integer.toString(this.regions.indexedChunkCount()),
            "{graves}", Integer.toString(this.graves.activeGraveCount()));
        this.messages.send(sender, "maintenance.status-runtime",
            "{skins}", Integer.toString(this.skins.selectedSkinCount()),
            "{cache}", Integer.toString(this.skins.cachedTextureCount()),
            "{teleports}", Integer.toString(this.teleports.pendingOperationCount()),
            "{vanished}", Integer.toString(this.staff.vanishedCount()),
            "{writes}", Integer.toString(this.dataStore.pendingWrites()));
    }

    private void runDoctor(final CommandSender sender) {
        List<String> issues = this.maintenance.diagnose();
        if (issues.isEmpty()) {
            this.messages.send(sender, "maintenance.doctor-ok");
            return;
        }
        this.messages.send(sender, "maintenance.doctor-header", "{count}", Integer.toString(issues.size()));
        for (String issue : issues) {
            this.messages.send(sender, "maintenance.doctor-entry", "{issue}", issue);
        }
    }

    private void createBackup(final CommandSender sender) {
        this.messages.send(sender, "maintenance.backup-started");
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            try {
                File backup = this.maintenance.createBackup();
                Bukkit.getScheduler().runTask(this.plugin, () -> {
                    this.messages.send(sender, "maintenance.backup-created", "{file}", backup.getName());
                    this.audit.log("PLUGIN_BACKUP", "actor=" + sender.getName() + " file=" + backup.getName());
                });
            } catch (IOException exception) {
                Bukkit.getScheduler().runTask(this.plugin,
                    () -> this.messages.send(sender, "maintenance.backup-failed", "{error}", exception.getMessage()));
            }
        });
    }

    private void persistData(final CommandSender sender) {
        this.authentication.persistData();
        this.skins.persistData();
        this.homes.persistData();
        this.teleports.persistData();
        this.playerInfo.persistData();
        this.staff.persistData();
        this.regions.persistData();
        this.graves.persistData();
        if (this.dataStore.flush()) {
            this.messages.send(sender, "maintenance.migrate-complete");
            this.audit.log("DATA_MIGRATION", "actor=" + sender.getName());
        } else {
            this.messages.send(sender, "maintenance.migrate-failed");
        }
    }

    private void sendHelp(final CommandSender sender, final String requestedCategory) {
        String key = switch (requestedCategory.toLowerCase(Locale.ROOT)) {
            case "categories" -> "help.categories";
            case "auth" -> "help.account";
            case "teleport" -> "help.teleports";
            case "skin" -> "help.skins";
            case "region" -> "help.regions";
            case "grave" -> "help.graves";
            case "social" -> "help.social";
            case "staff" -> "help.staff";
            case "admin" -> "help.admin";
            default -> "help.main";
        };
        this.messages.send(sender, "help.header");
        this.messages.send(sender, key);
    }

    @Override
    public List<String> onTabComplete(
        final CommandSender sender,
        final Command command,
        final String alias,
        final String[] args
    ) {
        if (args.length == 1) {
            List<String> actions = new ArrayList<>();
            if (sender.hasPermission("emsichill.homes.admin")) actions.add("homes");
            if (sender.hasPermission("emsichill.rtp.admin")) actions.add("rtp");
            if (sender.hasPermission("emsichill.admin.reload")) actions.add("reload");
            if (sender.hasPermission("emsichill.admin.maintenance")) {
                actions.addAll(List.of("status", "doctor", "backup", "migrate"));
            }
            actions.add("help");
            return CommandSuggestions.filter(actions, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("homes")
            && sender.hasPermission("emsichill.homes.admin")) {
            return CommandSuggestions.filter(List.of("limit"), args[1]);
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("rtp")
            && sender.hasPermission("emsichill.rtp.admin")) {
            if (args.length == 2) {
                return CommandSuggestions.filter(List.of("cooldown"), args[1]);
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("cooldown")) {
                return CommandSuggestions.filter(List.of("0", "5", "15", "30", "60"), args[2]);
            }
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("help")) {
            return CommandSuggestions.filter(List.of("categories", "auth", "teleport", "skin", "region",
                "grave", "social", "staff", "admin"), args[1]);
        }
        return Collections.emptyList();
    }
}