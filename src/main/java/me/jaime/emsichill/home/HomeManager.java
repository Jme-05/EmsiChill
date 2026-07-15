package me.jaime.emsichill.home;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import me.jaime.emsichill.Main;
import me.jaime.emsichill.config.ConfigFile;
import me.jaime.emsichill.teleport.TeleportManager;
import me.jaime.emsichill.util.CommandSuggestions;

/** Aplica límites y permisos de homes y coordina sus comandos con TeleportManager. */
public final class HomeManager implements CommandExecutor, TabCompleter {
    private final Main plugin;
    private final TeleportManager teleportManager;
    private final HomeRepository repository;
    private ConfigFile configFile;

    public HomeManager(final Main plugin, final TeleportManager teleportManager) {
        this.plugin = plugin;
        this.teleportManager = teleportManager;
        this.migrateLegacyConfig();
        this.reloadConfiguration();
        this.repository = new HomeRepository(plugin);
    }

    public void reloadConfiguration() {
        if (this.configFile == null) this.configFile = new ConfigFile(this.plugin, "Home/config.yml");
        else this.configFile.reload();
    }

    public void stop() {
        this.repository.save();
    }

    public void persistData() { this.repository.save(); }

    public boolean setDefaultHomeLimit(final CommandSender sender, final int limit) {
        if (limit < 0 || limit > 1000) {
            this.plugin.messages().send(sender, "teleport.home-limit-invalid");
            return false;
        }
        this.configFile.yaml().set("homes.default-limit", limit);
        if (!this.configFile.save()) {
            this.plugin.messages().send(sender, "general.save-error");
            return false;
        }
        this.plugin.messages().send(sender, "teleport.home-limit-updated", "{limit}", Integer.toString(limit));
        return true;
    }

    public int homeCount() {
        return this.repository.homeCount();
    }

    public int playerCount() {
        return this.repository.playerCount();
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (!this.plugin.moduleEnabled("homes")) {
            this.plugin.messages().send(sender, "general.module-disabled");
            return true;
        }
        if (!(sender instanceof Player player)) {
            this.plugin.messages().send(sender, "general.only-players");
            return true;
        }
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "sethome" -> this.setHome(player, args);
            case "home" -> this.goHome(player, args);
            case "delhome" -> this.deleteHome(player, args);
            case "homes" -> this.listHomes(player);
            default -> true;
        };
    }

    private boolean setHome(final Player player, final String[] args) {
        String name = args.length == 0 ? "home" : args[0].toLowerCase(Locale.ROOT);
        if (args.length > 1 || !name.matches("[a-z0-9_-]{1,24}")) {
            this.plugin.messages().send(player, "teleport.sethome-usage");
            return true;
        }
        Map<String, HomeLocation> playerHomes = this.repository.editableHomes(player.getName());
        this.repository.rememberPlayerName(player.getName());
        int limit = this.homeLimit(player);
        if (!playerHomes.containsKey(name) && playerHomes.size() >= limit) {
            this.plugin.messages().send(player, "teleport.home-limit", "{limit}", Integer.toString(limit));
            return true;
        }
        playerHomes.put(name, HomeLocation.from(player.getLocation()));
        this.repository.save();
        this.plugin.messages().send(player, "teleport.home-set", "{home}", name);
        return true;
    }

    private boolean goHome(final Player player, final String[] args) {
        if (args.length > 2) {
            this.plugin.messages().send(player, "teleport.home-usage");
            return true;
        }
        if (player.hasPermission("emsichill.homes.others") && args.length >= 1) {
            String targetKey = args[0].toLowerCase(Locale.ROOT);
            Map<String, HomeLocation> targetHomes = this.repository.homes(targetKey);
            if (!targetHomes.isEmpty()) {
                String targetName = this.repository.displayName(targetKey);
                if (args.length == 1) {
                    if (targetHomes.isEmpty()) this.plugin.messages().send(player, "teleport.admin-no-homes", "{player}", targetName);
                    else this.plugin.messages().send(player, "teleport.admin-home-list", "{player}", targetName,
                        "{homes}", String.join(", ", targetHomes.keySet()));
                    return true;
                }
                String targetHomeName = args[1].toLowerCase(Locale.ROOT);
                HomeLocation targetHome = targetHomes.get(targetHomeName);
                Location destination = targetHome == null ? null : targetHome.toLocation();
                if (destination == null) {
                    this.plugin.messages().send(player, "teleport.admin-home-not-found", "{home}", targetHomeName,
                        "{player}", targetName);
                    return true;
                }
                this.teleportManager.teleportFromHome(player, destination);
                return true;
            }
        }
        if (args.length > 1) {
            this.plugin.messages().send(player, "teleport.home-usage");
            return true;
        }
        String name = args.length == 0 ? "home" : args[0].toLowerCase(Locale.ROOT);
        HomeLocation saved = this.repository.homes(player.getName()).get(name);
        Location destination = saved == null ? null : saved.toLocation();
        if (destination == null) {
            this.plugin.messages().send(player, "teleport.home-not-found", "{home}", name);
            return true;
        }
        this.teleportManager.teleportFromHome(player, destination);
        return true;
    }

    private boolean deleteHome(final Player player, final String[] args) {
        if (args.length != 1) {
            this.plugin.messages().send(player, "teleport.delhome-usage");
            return true;
        }
        String name = args[0].toLowerCase(Locale.ROOT);
        Map<String, HomeLocation> playerHomes = this.repository.homes(player.getName());
        if (playerHomes.remove(name) == null) {
            this.plugin.messages().send(player, "teleport.home-not-found", "{home}", name);
            return true;
        }
        this.repository.removePlayerIfEmpty(player.getName());
        this.repository.save();
        this.plugin.messages().send(player, "teleport.home-deleted", "{home}", name);
        return true;
    }

    private boolean listHomes(final Player player) {
        Set<String> names = this.repository.homes(player.getName()).keySet();
        if (names.isEmpty()) this.plugin.messages().send(player, "teleport.no-homes");
        else this.plugin.messages().send(player, "teleport.home-list", "{homes}", String.join(", ", names));
        return true;
    }

    private int homeLimit(final Player player) {
        if (player.hasPermission("emsichill.homes.unlimited")) return Integer.MAX_VALUE;
        int limit = Math.max(0, this.configFile.yaml().getInt("homes.default-limit", 1));
        for (Map<?, ?> entry : this.configFile.yaml().getMapList("homes.permission-limits")) {
            Object permission = entry.get("permission");
            Object value = entry.get("limit");
            if (permission != null && value instanceof Number number && player.hasPermission(permission.toString())) {
                limit = Math.max(limit, number.intValue());
            }
        }
        return limit;
    }

    private void migrateLegacyConfig() {
        File target = new File(this.plugin.getDataFolder(), "Home/config.yml");
        if (target.exists()) return;
        File legacy = new File(this.plugin.getDataFolder(), "Teleport/config.yml");
        if (!legacy.exists()) return;
        YamlConfiguration old = this.plugin.dataStore().load(legacy);
        ConfigurationSection homesSection = old.getConfigurationSection("homes");
        if (homesSection == null) return;
        YamlConfiguration migrated = new YamlConfiguration();
        for (String key : homesSection.getKeys(true)) {
            if (!homesSection.isConfigurationSection(key)) migrated.set("homes." + key, homesSection.get(key));
        }
        this.plugin.dataStore().saveNow(target, migrated);
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args) {
        if (!(sender instanceof Player player)) return Collections.emptyList();
        if (command.getName().equalsIgnoreCase("home")) {
            if (args.length == 1) {
                List<String> values = new ArrayList<>(this.repository.homes(player.getName()).keySet());
                if (player.hasPermission("emsichill.homes.others")) values.addAll(this.repository.playerNames());
                return CommandSuggestions.filter(values, args[0]);
            }
            if (args.length == 2 && player.hasPermission("emsichill.homes.others")) {
                return CommandSuggestions.filter(this.repository.homes(args[0]).keySet(), args[1]);
            }
            return Collections.emptyList();
        }
        if (args.length == 1 && command.getName().equalsIgnoreCase("delhome")) {
            return CommandSuggestions.filter(this.repository.homes(player.getName()).keySet(), args[0]);
        }
        return Collections.emptyList();
    }

}