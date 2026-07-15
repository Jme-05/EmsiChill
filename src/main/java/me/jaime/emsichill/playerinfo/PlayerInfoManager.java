package me.jaime.emsichill.playerinfo;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import me.jaime.emsichill.Main;
import me.jaime.emsichill.config.ConfigFile;

/** Registra tiempo jugado y fechas de conexión para /playtime, /seen y la clasificación. */
public final class PlayerInfoManager implements CommandExecutor, TabCompleter, Listener {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault());

    private final Main plugin;
    private final File dataFile;
    private final Map<String, PlayerRecord> records = new ConcurrentHashMap<>();
    private final Map<UUID, Long> sessionStarts = new ConcurrentHashMap<>();
    private ConfigFile configFile;

    public PlayerInfoManager(final Main plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "PlayerInfo/data.yml");
        this.reloadConfiguration();
        this.loadData();
    }

    public void reloadConfiguration() {
        if (this.configFile == null) this.configFile = new ConfigFile(this.plugin, "PlayerInfo/config.yml");
        else this.configFile.reload();
    }

    public void start() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) this.startSession(player, now);
    }

    public void stop() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) this.endSession(player, now);
        this.saveData();
    }

    public int trackedPlayerCount() { return this.records.size(); }
    public void persistData() { this.saveData(); }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (!this.plugin.moduleEnabled("player-info")) {
            this.plugin.messages().send(sender, "general.module-disabled");
            return true;
        }
        if (command.getName().equalsIgnoreCase("playtimetop")) return this.showTop(sender);
        if (args.length > 1) {
            this.plugin.messages().send(sender, command.getName().equalsIgnoreCase("seen") ? "playerinfo.seen-usage" : "playerinfo.playtime-usage");
            return true;
        }
        String requested;
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                this.plugin.messages().send(sender, "playerinfo.playtime-usage");
                return true;
            }
            requested = player.getName();
        } else requested = args[0];

        PlayerRecord record = this.records.get(requested.toLowerCase(Locale.ROOT));
        if (record == null) {
            this.plugin.messages().send(sender, "playerinfo.not-found");
            return true;
        }
        if (command.getName().equalsIgnoreCase("playtime")) {
            this.plugin.messages().send(sender, "playerinfo.playtime", "{player}", record.name(),
                "{total}", this.formatDuration(this.totalTime(record)), "{first}", DATE_FORMAT.format(Instant.ofEpochMilli(record.firstSeen())));
        } else {
            Player online = this.visibleOnline(record.name(), sender);
            if (online != null) {
                long started = this.sessionStarts.getOrDefault(online.getUniqueId(), System.currentTimeMillis());
                this.plugin.messages().send(sender, "playerinfo.seen-online", "{player}", record.name(),
                    "{session}", this.formatDuration(System.currentTimeMillis() - started));
            } else {
                this.plugin.messages().send(sender, "playerinfo.seen-offline", "{player}", record.name(),
                    "{last}", DATE_FORMAT.format(Instant.ofEpochMilli(record.lastSeen())),
                    "{ago}", this.formatDuration(System.currentTimeMillis() - record.lastSeen()));
            }
        }
        return true;
    }

    private boolean showTop(final CommandSender sender) {
        int size = Math.max(1, Math.min(20, this.configFile.yaml().getInt("playtime-top-size", 10)));
        List<PlayerRecord> sorted = new ArrayList<>(this.records.values());
        sorted.sort(Comparator.comparingLong(this::totalTime).reversed());
        this.plugin.messages().send(sender, "playerinfo.top-header");
        for (int index = 0; index < Math.min(size, sorted.size()); index++) {
            PlayerRecord record = sorted.get(index);
            this.plugin.messages().send(sender, "playerinfo.top-entry", "{position}", Integer.toString(index + 1),
                "{player}", record.name(), "{total}", this.formatDuration(this.totalTime(record)));
        }
        return true;
    }

    // Para jugadores conectados se suma la sesión actual sin guardarla todavía.
    private long totalTime(final PlayerRecord record) {
        Player online = Bukkit.getPlayerExact(record.name());
        long active = online == null ? 0L : System.currentTimeMillis() - this.sessionStarts.getOrDefault(online.getUniqueId(), System.currentTimeMillis());
        return record.totalMillis() + Math.max(0L, active);
    }

    private Player visibleOnline(final String name, final CommandSender viewer) {
        Player player = Bukkit.getPlayerExact(name);
        if (player == null) return null;
        if (this.plugin.isVanished(player) && !viewer.hasPermission("emsichill.vanish.see")) return null;
        return player;
    }

    private String formatDuration(final long millis) {
        Duration duration = Duration.ofMillis(Math.max(0L, millis));
        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();
        if (days > 0) return days + "d " + hours + "h " + minutes + "m";
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m " + duration.toSecondsPart() + "s";
    }

    private void startSession(final Player player, final long now) {
        String key = player.getName().toLowerCase(Locale.ROOT);
        this.records.compute(key, (ignored, current) -> current == null
            ? new PlayerRecord(player.getName(), now, now, 0L)
            : new PlayerRecord(player.getName(), current.firstSeen(), now, current.totalMillis()));
        this.sessionStarts.put(player.getUniqueId(), now);
        this.saveData();
    }

    // Al salir, la sesión temporal se consolida en el acumulado persistente.
    private void endSession(final Player player, final long now) {
        Long start = this.sessionStarts.remove(player.getUniqueId());
        String key = player.getName().toLowerCase(Locale.ROOT);
        PlayerRecord current = this.records.get(key);
        if (current != null) {
            long added = start == null ? 0L : Math.max(0L, now - start);
            this.records.put(key, new PlayerRecord(player.getName(), current.firstSeen(), now, current.totalMillis() + added));
        }
        this.saveData();
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        if (this.plugin.moduleEnabled("player-info")) this.startSession(event.getPlayer(), System.currentTimeMillis());
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        if (this.plugin.moduleEnabled("player-info")) this.endSession(event.getPlayer(), System.currentTimeMillis());
    }

    // Los tiempos se almacenan por nombre normalizado para conservar consultas sin conexión.
    private void loadData() {
        if (!this.dataFile.exists()) return;
        YamlConfiguration yaml = this.plugin.dataStore().load(this.dataFile);
        ConfigurationSection section = yaml.getConfigurationSection("players");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            String path = "players." + key;
            String name = yaml.getString(path + ".name");
            if (name != null) this.records.put(key.toLowerCase(Locale.ROOT), new PlayerRecord(name,
                yaml.getLong(path + ".first-seen"), yaml.getLong(path + ".last-seen"), yaml.getLong(path + ".total-millis")));
        }
    }

    private void saveData() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, PlayerRecord> entry : this.records.entrySet()) {
            String path = "players." + entry.getKey();
            PlayerRecord record = entry.getValue();
            yaml.set(path + ".name", record.name()); yaml.set(path + ".first-seen", record.firstSeen());
            yaml.set(path + ".last-seen", record.lastSeen()); yaml.set(path + ".total-millis", record.totalMillis());
        }
        this.plugin.dataStore().saveAsync(this.dataFile, yaml);
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args) {
        if (args.length != 1) return Collections.emptyList();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>();
        for (PlayerRecord record : this.records.values()) {
            if (record.name().toLowerCase(Locale.ROOT).startsWith(prefix)) names.add(record.name());
        }
        Collections.sort(names);
        return names;
    }

}