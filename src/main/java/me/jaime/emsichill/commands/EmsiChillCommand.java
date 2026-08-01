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
import org.bukkit.entity.Player;

import me.jaime.emsichill.Main;
import me.jaime.emsichill.auth.AuthenticationManager;
import me.jaime.emsichill.config.Messages;
import me.jaime.emsichill.documentation.CommandDoc;
import me.jaime.emsichill.documentation.CommandDocumentation;
import me.jaime.emsichill.grave.GraveManager;
import me.jaime.emsichill.home.HomeManager;
import me.jaime.emsichill.maintenance.MaintenanceService;
import me.jaime.emsichill.playerinfo.PlayerInfoManager;
import me.jaime.emsichill.region.RegionManager;
import me.jaime.emsichill.resourcepack.ResourcePackManager;
import me.jaime.emsichill.staff.StaffService;
import me.jaime.emsichill.staff.ModerationService;
import me.jaime.emsichill.storage.DataStore;
import me.jaime.emsichill.teleport.TeleportManager;
import me.jaime.emsichill.update.PaperUpdateInstallResult;
import me.jaime.emsichill.update.PaperUpdateResult;
import me.jaime.emsichill.update.UpdateResult;
import me.jaime.emsichill.update.ReleaseNotesFormatter;
import me.jaime.emsichill.update.UpdateInstallResult;
import me.jaime.emsichill.update.UpdateService;
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
    private final StaffService staff;
    private final ModerationService moderation;
    private final RegionManager regions;
    private final GraveManager graves;
    private final UpdateService updates;
    private final ResourcePackManager resourcePacks;
    private final CommandDocumentation documentation;

    public EmsiChillCommand(
        final Main plugin,
        final MaintenanceService maintenance,
        final AuthenticationManager authentication,
        final SkinCommand skins,
        final TeleportManager teleports,
        final HomeManager homes,
        final PlayerInfoManager playerInfo,
        final StaffService staff,
        final ModerationService moderation,
        final RegionManager regions,
        final GraveManager graves,
        final UpdateService updates,
        final ResourcePackManager resourcePacks,
        final CommandDocumentation documentation
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
        this.moderation = moderation;
        this.regions = regions;
        this.graves = graves;
        this.updates = updates;
        this.resourcePacks = resourcePacks;
        this.documentation = documentation;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (args.length == 3 && args[0].equalsIgnoreCase("rtp") && args[1].equalsIgnoreCase("cooldown")) {
            return this.changeRtpCooldown(sender, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("homes") && args[1].equalsIgnoreCase("limit")) {
            return this.changeHomeLimit(sender, args[2]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("language")) {
            return this.changeLanguage(sender, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("rp") && args[1].equalsIgnoreCase("reload")) {
            return this.reloadResourcePacks(sender);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("rp") && args[1].equalsIgnoreCase("push")) {
            return this.pushResourcePacks(sender);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("update") && args[1].equalsIgnoreCase("check")) {
            return this.checkUpdates(sender);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("update")
            && args[1].equalsIgnoreCase("paper") && args[2].equalsIgnoreCase("check")) {
            return this.checkPaperUpdates(sender);
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("update")
            && args[1].equalsIgnoreCase("paper") && args[2].equalsIgnoreCase("download")) {
            return this.downloadPaperUpdate(sender, args[3], args[4]);
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("update")
            && args[1].equalsIgnoreCase("paper") && args[2].equalsIgnoreCase("ignore")) {
            return this.ignorePaperUpdate(sender, args[3], args[4]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("update")
            && args[1].equalsIgnoreCase("install")) {
            return this.installUpdate(sender, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("update")
            && args[1].equalsIgnoreCase("ignore")) {
            return this.ignoreUpdate(sender, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("update")
            && args[1].equalsIgnoreCase("changes")) {
            return this.showUpdateChanges(sender, args[2]);
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            return this.reload(sender);
        }
        if (args.length == 1 && List.of("status", "inspect", "backup", "migrate")
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

    private boolean checkPaperUpdates(final CommandSender sender) {
        if (!sender.hasPermission("emsichill.admin.update")) {
            this.messages.send(sender, "general.no-permission");
            return true;
        }
        this.messages.send(sender, "update.paper-checking");
        this.updates.checkPaper().whenComplete((result, failure) -> Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (failure != null) {
                this.plugin.getLogger().warning("PaperMC check failed: " + failure.getMessage());
                this.messages.send(sender, "update.paper-failed");
                return;
            }
            if (result.status() == PaperUpdateResult.Status.UPDATE_AVAILABLE) {
                this.messages.sendLink(sender, "update.paper-available", result.latest().pageUrl(),
                    "{current}", result.current().label(),
                    "{version}", result.latest().minecraftVersion(),
                    "{build}", Integer.toString(result.latest().build()),
                    "{channel}", result.latest().channel());
                if (sender instanceof Player player) {
                    this.messages.sendPaperUpdateActions(player, result.latest().minecraftVersion(),
                        result.latest().build());
                }
                return;
            }
            if (result.status() == PaperUpdateResult.Status.UP_TO_DATE) {
                this.messages.send(sender, "update.paper-up-to-date",
                    "{current}", result.current().label(),
                    "{version}", result.latest().minecraftVersion(),
                    "{build}", Integer.toString(result.latest().build()));
                return;
            }
            this.plugin.getLogger().warning("Could not check PaperMC: " + result.error());
            this.messages.send(sender, "update.paper-failed");
        }));
        return true;
    }

    private boolean downloadPaperUpdate(
        final CommandSender sender,
        final String version,
        final String buildValue
    ) {
        if (!sender.hasPermission("emsichill.admin.update")) {
            this.messages.send(sender, "general.no-permission");
            return true;
        }
        int build;
        try {
            build = Integer.parseInt(buildValue);
        } catch (NumberFormatException exception) {
            this.messages.send(sender, "update.paper-build-invalid");
            return true;
        }
        if (build <= 0) {
            this.messages.send(sender, "update.paper-build-invalid");
            return true;
        }
        this.messages.send(sender, "update.paper-download-started",
            "{version}", version,
            "{build}", Integer.toString(build));
        this.updates.downloadPaper(version, build).whenComplete((result, failure) ->
            Bukkit.getScheduler().runTask(this.plugin, () -> this.finishPaperDownload(sender, result, failure)));
        return true;
    }

    private void finishPaperDownload(
        final CommandSender sender,
        final PaperUpdateInstallResult result,
        final Throwable failure
    ) {
        if (failure != null || result == null) {
            this.plugin.getLogger().warning("Paper download failed: "
                + (failure == null ? "empty result" : failure.getMessage()));
            this.messages.send(sender, "update.paper-download-failed");
            return;
        }
        switch (result.status()) {
            case PREPARED -> {
                this.updates.markPaperStaged(result.target());
                this.messages.send(sender, "update.paper-download-prepared",
                    "{target}", result.target(),
                    "{file}", result.file().toString());
                this.audit.log("PAPER_UPDATE_PREPARED", "actor=" + sender.getName()
                    + " target=" + result.target() + " file=" + result.file());
            }
            case NO_UPDATE -> this.messages.send(sender, "update.paper-no-update");
            case VERSION_CHANGED -> this.messages.send(sender, "update.paper-version-changed",
                "{target}", result.target());
            case IN_PROGRESS -> this.messages.send(sender, "update.paper-download-in-progress");
            case DISABLED -> this.messages.send(sender, "update.paper-download-disabled");
            case FAILED -> {
                this.plugin.getLogger().warning("Could not prepare Paper " + result.target()
                    + ": " + result.error());
                this.messages.send(sender, "update.paper-download-failed");
            }
        }
    }

    private boolean ignorePaperUpdate(final CommandSender sender, final String version, final String buildValue) {
        if (!sender.hasPermission("emsichill.admin.update")) {
            this.messages.send(sender, "general.no-permission");
            return true;
        }
        int build;
        try {
            build = Integer.parseInt(buildValue);
        } catch (NumberFormatException exception) {
            this.messages.send(sender, "update.paper-build-invalid");
            return true;
        }
        if (!this.updates.ignorePaper(version, build)) {
            this.messages.send(sender, "update.paper-ignore-failed");
            return true;
        }
        this.messages.send(sender, "update.paper-ignored",
            "{version}", version,
            "{build}", Integer.toString(build));
        this.audit.log("PAPER_UPDATE_IGNORED", "actor=" + sender.getName()
            + " target=" + version + "#" + build);
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

    private boolean changeLanguage(final CommandSender sender, final String value) {
        String language = normalizeLanguage(value);
        if (language == null) {
            this.messages.send(sender, "language.invalid");
            return true;
        }
        if (sender instanceof Player player) {
            if (!this.messages.setPlayerLanguage(player, language)) {
                this.messages.send(sender, "general.save-error");
                return true;
            }
            this.audit.log("PLAYER_LANGUAGE_CHANGE", "actor=" + sender.getName() + " language=" + language);
            this.messages.send(sender, "language.changed", "{language}", this.languageName(sender, language));
            return true;
        }
        if (!sender.hasPermission("emsichill.admin.language")) {
            this.messages.send(sender, "general.no-permission");
            return true;
        }
        this.plugin.settings().set("language", language);
        if (!this.plugin.saveSettings()) {
            this.messages.send(sender, "general.save-error");
            return true;
        }
        this.plugin.reloadPlugin();
        this.audit.log("GLOBAL_LANGUAGE_CHANGE", "actor=" + sender.getName() + " language=" + language);
        this.messages.send(sender, "language.changed-global", "{language}", this.languageName(sender, language));
        return true;
    }

    private boolean reloadResourcePacks(final CommandSender sender) {
        if (!sender.hasPermission("emsichill.resourcepack.admin")) {
            this.messages.send(sender, "general.no-permission");
            return true;
        }
        if (!this.plugin.moduleEnabled("resource-packs")) {
            this.plugin.settings().set("modules.resource-packs", true);
            if (!this.plugin.saveSettings()) {
                this.messages.send(sender, "general.save-error");
                return true;
            }
        }
        this.messages.send(sender, "resource-pack.reloading");
        this.resourcePacks.reloadLocalPacks().whenComplete((result, failure) -> {
            if (failure != null || result == null) {
                this.plugin.getLogger().warning("Local resource-pack reload failed: "
                    + (failure == null ? "empty result" : failure.getMessage()));
                this.messages.send(sender, "resource-pack.failed");
                return;
            }
            switch (result.status()) {
                case LOADED -> {
                    this.messages.send(sender, "resource-pack.loaded",
                        "{sources}", Integer.toString(result.sources()));
                    this.audit.log("RESOURCE_PACK_RELOAD", "actor=" + sender.getName()
                        + " sources=" + result.sources() + " hashes=" + result.sha1());
                }
                case EMPTY -> this.messages.send(sender, "resource-pack.empty");
                case IN_PROGRESS -> this.messages.send(sender, "resource-pack.in-progress");
                case DISABLED -> this.messages.send(sender, "general.module-disabled");
                case FAILED -> {
                    this.plugin.getLogger().warning("Local resource-pack reload failed: " + result.error());
                    this.messages.send(sender, "resource-pack.failed");
                }
            }
        });
        return true;
    }

    private boolean pushResourcePacks(final CommandSender sender) {
        if (!sender.hasPermission("emsichill.resourcepack.admin")) {
            this.messages.send(sender, "general.no-permission");
            return true;
        }
        ResourcePackManager.PushResult result = this.resourcePacks.pushOnline();
        switch (result.status()) {
            case SENT -> {
                this.messages.send(sender, "resource-pack.pushed",
                    "{sources}", Integer.toString(result.packs()),
                    "{players}", Integer.toString(result.players()));
                this.audit.log("RESOURCE_PACK_PUSH", "actor=" + sender.getName()
                    + " sources=" + result.packs() + " players=" + result.players());
            }
            case EMPTY -> this.messages.send(sender, "resource-pack.empty");
            case DISABLED -> this.messages.send(sender, "general.module-disabled");
            case FAILED -> {
                this.plugin.getLogger().warning("Local resource-pack push failed: " + result.error());
                this.messages.send(sender, "resource-pack.failed");
            }
        }
        return true;
    }

    private static String normalizeLanguage(final String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "en", "english" -> "en";
            case "es", "spanish", "espanol", "español" -> "es";
            default -> null;
        };
    }

    private String languageName(final CommandSender sender, final String language) {
        return language.equals("es") ? this.messages.plain(sender, "language.spanish") : this.messages.plain(sender, "language.english");
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

    private boolean checkUpdates(final CommandSender sender) {
        if (!sender.hasPermission("emsichill.admin.update")) {
            this.messages.send(sender, "general.no-permission");
            return true;
        }
        this.messages.send(sender, "update.checking");
        this.updates.check().whenComplete((result, failure) -> Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (failure != null) {
                this.plugin.getLogger().warning("Update check failed: " + failure.getMessage());
                this.messages.send(sender, "update.failed");
                return;
            }
            if (result.status() == UpdateResult.Status.UPDATE_AVAILABLE) {
                this.messages.sendLink(sender, "update.available", result.release().pageUrl(),
                    "{current}", result.currentVersion(),
                    "{latest}", result.release().tag());
                if (sender instanceof Player player) {
                    this.messages.sendUpdateActions(player, result.release().tag());
                }
                return;
            }
            if (result.status() == UpdateResult.Status.UP_TO_DATE) {
                this.messages.send(sender, "update.up-to-date", "{version}", result.currentVersion());
                return;
            }
            this.plugin.getLogger().warning("Could not check for updates: " + result.error());
            this.messages.send(sender, "update.failed");
        }));
        return true;
    }

    private boolean installUpdate(final CommandSender sender, final String version) {
        if (!sender.hasPermission("emsichill.admin.update")) {
            this.messages.send(sender, "general.no-permission");
            return true;
        }
        this.messages.send(sender, "update.install-started", "{version}", version);
        this.updates.install(version).whenComplete((result, failure) ->
            Bukkit.getScheduler().runTask(this.plugin, () -> this.finishUpdateInstall(sender, result, failure)));
        return true;
    }

    private void finishUpdateInstall(
        final CommandSender sender,
        final UpdateInstallResult result,
        final Throwable failure
    ) {
        if (failure != null || result == null) {
            this.plugin.getLogger().warning("Update preparation failed: "
                + (failure == null ? "empty result" : failure.getMessage()));
            this.messages.send(sender, "update.install-failed");
            return;
        }
        switch (result.status()) {
            case PREPARED -> {
                this.updates.markStaged(result.version());
                this.messages.send(sender, "update.install-prepared", "{version}", result.version());
                this.audit.log("UPDATE_PREPARED", "actor=" + sender.getName() + " version=" + result.version());
            }
            case NO_UPDATE -> this.messages.send(sender, "update.up-to-date",
                "{version}", this.plugin.getPluginMeta().getVersion());
            case VERSION_CHANGED -> this.messages.send(sender, "update.version-changed",
                "{version}", result.version());
            case IN_PROGRESS -> this.messages.send(sender, "update.install-in-progress");
            case DISABLED -> this.messages.send(sender, "update.install-disabled");
            case FAILED -> {
                this.plugin.getLogger().warning("Could not prepare EmsiChill " + result.version()
                    + ": " + result.error());
                this.messages.send(sender, "update.install-failed");
            }
        }
    }

    private boolean ignoreUpdate(final CommandSender sender, final String version) {
        if (!sender.hasPermission("emsichill.admin.update")) {
            this.messages.send(sender, "general.no-permission");
            return true;
        }
        if (!this.updates.ignore(version)) {
            this.messages.send(sender, "update.ignore-failed");
            return true;
        }
        this.messages.send(sender, "update.ignored", "{version}", version);
        this.audit.log("UPDATE_IGNORED", "actor=" + sender.getName() + " version=" + version);
        return true;
    }

    private boolean showUpdateChanges(final CommandSender sender, final String version) {
        if (!sender.hasPermission("emsichill.admin.update")) {
            this.messages.send(sender, "general.no-permission");
            return true;
        }
        this.messages.send(sender, "update.changes-loading", "{version}", version);
        this.updates.check().whenComplete((result, failure) -> Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (failure != null || result == null || result.status() == UpdateResult.Status.FAILED) {
                this.messages.send(sender, "update.failed");
                return;
            }
            if (!this.updates.matchesVersion(version, result.release().tag())) {
                this.messages.send(sender, "update.version-changed", "{version}", result.release().tag());
                return;
            }
            List<String> notes = ReleaseNotesFormatter.format(result.release().notes());
            this.messages.send(sender, "update.changes-header", "{version}", result.release().tag());
            if (notes.isEmpty()) {
                this.messages.send(sender, "update.changes-empty");
                return;
            }
            notes.forEach(line -> this.messages.sendReleaseLine(sender, line));
        }));
        return true;
    }

    private boolean handleMaintenance(final CommandSender sender, final String action) {
        switch (action) {
            case "status" -> this.sendStatus(sender);
            case "inspect" -> this.runInspection(sender);
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

    private void runInspection(final CommandSender sender) {
        List<String> issues = this.maintenance.diagnose();
        if (issues.isEmpty()) {
            this.messages.send(sender, "maintenance.inspect-ok");
            return;
        }
        this.messages.send(sender, "maintenance.inspect-header", "{count}", Integer.toString(issues.size()));
        for (String issue : issues) {
            this.messages.send(sender, "maintenance.inspect-entry", "{issue}", issue);
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
        this.moderation.persistData();
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
        String category = requestedCategory.toLowerCase(Locale.ROOT);
        this.messages.send(sender, "help.header");
        if (category.equals("categories")) {
            this.messages.send(sender, "help.categories", "{categories}", String.join(", ", this.visibleHelpCategories(sender)));
            return;
        }
        if (!this.canViewHelpCategory(sender, category)) {
            this.messages.send(sender, "general.no-permission");
            return;
        }
        if (!this.documentation.hasCategory(category)) {
            this.messages.send(sender, "help.main");
            return;
        }
        int shown = 0;
        for (CommandDoc entry : this.documentation.entriesForCategory(category)) {
            if (!this.canViewHelpEntry(sender, entry)) continue;
            this.messages.sendText(sender, "&f" + entry.command() + " &8- &7" + entry.description());
            shown++;
        }
        if (shown == 0) this.messages.send(sender, "general.no-permission");
    }

    private List<String> visibleHelpCategories(final CommandSender sender) {
        List<String> categories = new ArrayList<>(List.of("auth", "teleport", "skin", "region", "social"));
        if (this.canViewHelpCategory(sender, "grave")) categories.add("grave");
        if (this.canViewHelpCategory(sender, "staff")) categories.add("staff");
        if (this.canViewHelpCategory(sender, "admin")) categories.add("admin");
        return categories;
    }

    private boolean canViewHelpCategory(final CommandSender sender, final String category) {
        return switch (category.toLowerCase(Locale.ROOT)) {
            case "grave" -> sender.hasPermission("emsichill.grave.admin");
            case "staff" -> this.hasAnyPermission(sender, "emsichill.staffchat", "emsichill.vanish",
                "emsichill.vanish.see", "emsichill.staffmode", "emsichill.invsee.view",
                "emsichill.enderchestsee.view", "emsichill.freeze", "emsichill.mute",
                "emsichill.unmute", "emsichill.warn", "emsichill.warnings",
                "emsichill.skin.others", "emsichill.homes.others", "emsichill.back.others",
                "emsichill.auth.admin", "emsichill.grave.admin");
            case "admin" -> this.hasAnyPermission(sender, "emsichill.admin.reload", "emsichill.admin.maintenance",
                "emsichill.admin.update", "emsichill.admin.language", "emsichill.homes.admin",
                "emsichill.rtp.admin", "emsichill.deathcontrol.admin", "emsichill.auth.admin",
                "emsichill.resourcepack.admin");
            default -> true;
        };
    }

    private boolean hasAnyPermission(final CommandSender sender, final String... permissions) {
        for (String permission : permissions) if (sender.hasPermission(permission)) return true;
        return false;
    }

    private boolean canViewHelpEntry(final CommandSender sender, final CommandDoc entry) {
        if (!entry.category().equalsIgnoreCase("staff") && !entry.category().equalsIgnoreCase("admin")
            && !entry.category().equalsIgnoreCase("grave")) return true;
        String command = entry.command().toLowerCase(Locale.ROOT);
        if (entry.category().equalsIgnoreCase("grave")) return sender.hasPermission("emsichill.grave.admin");
        if (command.startsWith("/staffchat")) return sender.hasPermission("emsichill.staffchat");
        if (command.startsWith("/vanishlist")) return sender.hasPermission("emsichill.vanish.see");
        if (command.startsWith("/vanish")) return sender.hasPermission("emsichill.vanish");
        if (command.startsWith("/staffmode")) return sender.hasPermission("emsichill.staffmode");
        if (command.startsWith("/invsee")) return sender.hasPermission("emsichill.invsee.view");
        if (command.startsWith("/enderchestsee")) return sender.hasPermission("emsichill.enderchestsee.view");
        if (command.startsWith("/freeze")) return sender.hasPermission("emsichill.freeze");
        if (command.startsWith("/mute")) return sender.hasPermission("emsichill.mute");
        if (command.startsWith("/unmute")) return sender.hasPermission("emsichill.unmute");
        if (command.startsWith("/warn ")) return sender.hasPermission("emsichill.warn");
        if (command.startsWith("/warnings")) return sender.hasPermission("emsichill.warnings");
        if (command.startsWith("/skin <player>")) return sender.hasPermission("emsichill.skin.others");
        if (command.startsWith("/home <player>")) return sender.hasPermission("emsichill.homes.others");
        if (command.startsWith("/back <player>")) return sender.hasPermission("emsichill.back.others");
        if (command.startsWith("/auth")) return sender.hasPermission("emsichill.auth.admin");
        if (command.startsWith("/grave")) return sender.hasPermission("emsichill.grave.admin");
        if (command.startsWith("/deathcontrol")) return sender.hasPermission("emsichill.deathcontrol.admin");
        if (command.startsWith("/emsichill language")) return sender instanceof Player
            || sender.hasPermission("emsichill.admin.language");
        if (command.startsWith("/emsichill homes")) return sender.hasPermission("emsichill.homes.admin");
        if (command.startsWith("/emsichill rtp")) return sender.hasPermission("emsichill.rtp.admin");
        if (command.startsWith("/emsichill update")) return sender.hasPermission("emsichill.admin.update");
        if (command.startsWith("/emsichill rp")) return sender.hasPermission("emsichill.resourcepack.admin");
        if (command.startsWith("/emsichill reload")) return sender.hasPermission("emsichill.admin.reload");
        if (command.startsWith("/emsichill status") || command.startsWith("/emsichill inspect")
            || command.startsWith("/emsichill backup") || command.startsWith("/emsichill migrate")) {
            return sender.hasPermission("emsichill.admin.maintenance");
        }
        return false;
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
            if (sender instanceof Player || sender.hasPermission("emsichill.admin.language")) actions.add("language");
            if (sender.hasPermission("emsichill.admin.maintenance")) {
                actions.addAll(List.of("status", "inspect", "backup", "migrate"));
            }
            if (sender.hasPermission("emsichill.admin.update")) actions.add("update");
            if (sender.hasPermission("emsichill.resourcepack.admin")) actions.add("rp");
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
        if (args.length == 2 && args[0].equalsIgnoreCase("language")
            && (sender instanceof Player || sender.hasPermission("emsichill.admin.language"))) {
            return CommandSuggestions.filter(List.of("english", "spanish"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("rp")
            && sender.hasPermission("emsichill.resourcepack.admin")) {
            return CommandSuggestions.filter(List.of("reload", "push"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("help")) {
            List<String> categories = new ArrayList<>(List.of("categories"));
            categories.addAll(this.visibleHelpCategories(sender));
            return CommandSuggestions.filter(categories, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("update")
            && sender.hasPermission("emsichill.admin.update")) {
            return CommandSuggestions.filter(List.of("check", "changes", "install", "ignore", "paper"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("update") && args[1].equalsIgnoreCase("paper")
            && sender.hasPermission("emsichill.admin.update")) {
            return CommandSuggestions.filter(List.of("check", "download", "ignore"), args[2]);
        }
        return Collections.emptyList();
    }
}
