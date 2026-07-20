package me.jaime.emsichill.teleport;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import me.jaime.emsichill.Main;
import me.jaime.emsichill.config.ConfigFile;
import me.jaime.emsichill.util.CommandSuggestions;

/**
 * Coordina TPA, /back y RTP, incluidos retrasos, cancelación por movimiento, cooldowns y
 * persistencia de preferencias.
 */
public final class TeleportManager implements CommandExecutor, TabCompleter, Listener {
    private final Main plugin;
    private final RtpLocationFinder locationFinder = new RtpLocationFinder();
    private final File dataFile;
    private final Map<String, String> playerNames = new ConcurrentHashMap<>();
    private final Map<UUID, SavedLocation> backLocations = new ConcurrentHashMap<>();
    private final Map<UUID, TpaRequest> incomingRequests = new ConcurrentHashMap<>();
    private final Map<UUID, PendingTeleport> pendingTeleports = new ConcurrentHashMap<>();
    private final Map<UUID, RtpSearch> pendingRtpSearches = new ConcurrentHashMap<>();
    private final Set<String> tpaDisabled = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> rtpCooldowns = new ConcurrentHashMap<>();
    private ConfigFile configFile;

    public TeleportManager(final Main plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "Teleport/data.yml");
        this.reloadConfiguration();
        this.loadData();
    }

    public void reloadConfiguration() {
        if (this.configFile == null) this.configFile = new ConfigFile(this.plugin, "Teleport/config.yml");
        else this.configFile.reload();
    }

    public void stop() {
        for (PendingTeleport pending : this.pendingTeleports.values()) pending.task().cancel();
        this.pendingTeleports.clear();
        for (RtpSearch search : this.pendingRtpSearches.values()) search.cancel();
        this.pendingRtpSearches.clear();
        this.saveData();
    }

    public int pendingOperationCount() {
        return this.pendingTeleports.size() + this.pendingRtpSearches.size() + this.incomingRequests.size();
    }

    public void persistData() { this.saveData(); }

    public boolean setRtpCooldownMinutes(final CommandSender sender, final long minutes) {
        if (minutes < 0L || minutes > 10080L) {
            this.plugin.messages().send(sender, "teleport.rtp-cooldown-invalid");
            return false;
        }
        this.configFile.yaml().set("rtp.cooldown-seconds", minutes * 60L);
        if (!this.configFile.save()) {
            this.plugin.messages().send(sender, "general.save-error");
            return false;
        }
        if (minutes == 0L) this.rtpCooldowns.clear();
        this.plugin.messages().send(sender, "teleport.rtp-cooldown-set", "{minutes}", Long.toString(minutes));
        return true;
    }

    public void rememberDeathGrave(final Player player, final Location graveLocation) {
        if (!this.configFile.yaml().getBoolean("back.save-death-location", true)
            || graveLocation == null || graveLocation.getWorld() == null) return;
        Location destination = graveLocation.clone().add(0.5, 1.0, 0.5);
        this.backLocations.put(player.getUniqueId(), SavedLocation.from(destination));
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (!this.plugin.moduleEnabled("teleport")) {
            this.plugin.messages().send(sender, "general.module-disabled");
            return true;
        }
        Player player = sender instanceof Player value ? value : null;
        if (player == null) {
            this.plugin.messages().send(sender, "general.only-players");
            return true;
        }
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "tpa" -> this.requestTeleport(player, args, false);
            case "tpahere" -> this.requestTeleport(player, args, true);
            case "tpaccept" -> this.acceptTeleport(player);
            case "tpdeny" -> this.denyTeleport(player);
            case "tpcancel" -> this.cancelRequest(player);
            case "tptoggle" -> this.toggleRequests(player);
            case "back" -> this.goBackCommand(player, args);
            case "rtp" -> this.randomTeleport(player);
            default -> true;
        };
    }

    private void instantTeleport(final Player player, final Location destination, final boolean saveBack) {
        PendingTeleport pending = this.pendingTeleports.remove(player.getUniqueId());
        if (pending != null) pending.task().cancel();
        RtpSearch search = this.pendingRtpSearches.remove(player.getUniqueId());
        if (search != null) search.cancel();
        if (saveBack) this.backLocations.put(player.getUniqueId(), SavedLocation.from(player.getLocation()));
        player.teleport(destination);
    }

    public void teleportFromHome(final Player player, final Location destination) {
        this.instantTeleport(player, destination, true);
    }

    // Solo se conserva la solicitud entrante más reciente de cada destinatario.
    private boolean requestTeleport(final Player requester, final String[] args, final boolean here) {
        if (args.length != 1) {
            this.plugin.messages().send(requester, here ? "teleport.tpahere-usage" : "teleport.tpa-usage");
            return true;
        }
        Player target = this.findOnline(args[0]);
        if (target == null || target.equals(requester)) {
            this.plugin.messages().send(requester, "teleport.player-not-found");
            return true;
        }
        if (this.tpaDisabled.contains(this.userKey(target))) {
            this.plugin.messages().send(requester, "teleport.requests-disabled", "{player}", target.getName());
            return true;
        }
        long expires = Instant.now().plusSeconds(Math.max(5L,
            this.configFile.yaml().getLong("tpa.request-timeout-seconds", 30L))).getEpochSecond();
        this.incomingRequests.put(target.getUniqueId(), new TpaRequest(requester.getUniqueId(), target.getUniqueId(), here, expires));
        this.plugin.messages().send(requester, "teleport.request-sent", "{player}", target.getName());
        this.plugin.messages().send(target, here ? "teleport.request-here-received" : "teleport.request-received",
            "{player}", requester.getName());
        this.plugin.messages().sendTeleportRequestActions(target);
        return true;
    }

    private boolean acceptTeleport(final Player target) {
        TpaRequest request = this.incomingRequests.remove(target.getUniqueId());
        Player requester = request == null ? null : Bukkit.getPlayer(request.requester());
        if (request == null || request.expiresAt() < Instant.now().getEpochSecond() || requester == null) {
            this.plugin.messages().send(target, "teleport.no-request");
            return true;
        }
        Player moving = request.here() ? target : requester;
        Player destination = request.here() ? requester : target;
        this.plugin.messages().send(target, "teleport.request-accepted");
        this.plugin.messages().send(requester, "teleport.request-accepted");
        this.delayedTeleport(moving, destination.getLocation(), "tpa", true);
        return true;
    }

    private boolean denyTeleport(final Player target) {
        TpaRequest request = this.incomingRequests.remove(target.getUniqueId());
        if (request == null) {
            this.plugin.messages().send(target, "teleport.no-request");
            return true;
        }
        Player requester = Bukkit.getPlayer(request.requester());
        if (requester != null) this.plugin.messages().send(requester, "teleport.request-denied");
        this.plugin.messages().send(target, "teleport.request-denied");
        return true;
    }

    private boolean cancelRequest(final Player requester) {
        boolean removed = this.incomingRequests.entrySet().removeIf(entry -> entry.getValue().requester().equals(requester.getUniqueId()));
        if (removed) this.plugin.messages().send(requester, "teleport.request-cancelled");
        else this.plugin.messages().send(requester, "teleport.no-request");
        return true;
    }

    private boolean toggleRequests(final Player player) {
        String key = this.userKey(player);
        boolean disabled;
        if (this.tpaDisabled.remove(key)) disabled = false;
        else {
            this.tpaDisabled.add(key);
            disabled = true;
        }
        this.saveData();
        this.plugin.messages().send(player, disabled ? "teleport.toggle-off" : "teleport.toggle-on");
        return true;
    }

    private boolean goBack(final Player player) {
        SavedLocation saved = this.backLocations.get(player.getUniqueId());
        Location destination = saved == null ? null : saved.toLocation();
        if (destination == null) {
            this.plugin.messages().send(player, "teleport.no-back");
            return true;
        }
        SavedLocation current = SavedLocation.from(player.getLocation());
        this.delayedTeleport(player, destination, "back", false,
            () -> this.backLocations.put(player.getUniqueId(), current));
        return true;
    }

    private boolean goBackCommand(final Player player, final String[] args) {
        if (args.length == 0) return this.goBack(player);
        if (args.length != 1 || !player.hasPermission("emsichill.back.others")) {
            this.plugin.messages().send(player, "teleport.back-usage");
            return true;
        }
        Player target = this.findOnline(args[0]);
        if (target == null) {
            this.plugin.messages().send(player, "teleport.player-not-found");
            return true;
        }
        this.plugin.messages().send(player, "teleport.back-other", "{player}", target.getName());
        return this.goBack(target);
    }

    private boolean randomTeleport(final Player player) {
        long cooldown = Math.max(0L, this.configFile.yaml().getLong("rtp.cooldown-seconds", 1800L));
        long elapsed = Instant.now().getEpochSecond() - this.rtpCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (!player.hasPermission("emsichill.rtp.bypasscooldown") && elapsed < cooldown) {
            long remaining = cooldown - elapsed;
            this.plugin.messages().send(player, "teleport.rtp-cooldown",
                "{minutes}", Long.toString(remaining / 60L), "{seconds}", Long.toString(remaining % 60L));
            return true;
        }
        List<String> allowedWorlds = this.configFile.yaml().getStringList("rtp.allowed-worlds");
        if (!allowedWorlds.isEmpty() && allowedWorlds.stream().noneMatch(name -> name.equalsIgnoreCase(player.getWorld().getName()))) {
            this.plugin.messages().send(player, "teleport.rtp-world-disabled");
            return true;
        }
        RtpSearch previous = this.pendingRtpSearches.remove(player.getUniqueId());
        if (previous != null) previous.cancel();
        long delay = Math.max(0L, this.configFile.yaml().getLong("rtp.delay-seconds", 3L));
        RtpSearch search = new RtpSearch(player.getLocation().clone(), System.currentTimeMillis(), delay);
        this.pendingRtpSearches.put(player.getUniqueId(), search);
        if (delay > 0) {
            this.plugin.messages().send(player, "teleport.rtp-starting", "{seconds}", Long.toString(delay));
        }
        int attempts = Math.max(1, Math.min(100, this.configFile.yaml().getInt("rtp.maximum-attempts", 25)));
        this.findRandomLocationAsync(player, player.getWorld(), search, 0, attempts);
        return true;
    }

    // RTP carga cada chunk sin bloquear el hilo principal y valida el terreno en su callback.
    private void findRandomLocationAsync(final Player player, final World world, final RtpSearch search,
                                         final int attempt, final int maximumAttempts) {
        if (this.pendingRtpSearches.get(player.getUniqueId()) != search || search.cancelled()) return;
        if (attempt >= maximumAttempts) {
            this.pendingRtpSearches.remove(player.getUniqueId(), search);
            this.plugin.messages().send(player, "teleport.rtp-not-found");
            return;
        }
        int minimum = Math.max(0, this.configFile.yaml().getInt("rtp.minimum-radius", 500));
        int maximum = Math.max(minimum + 1, this.configFile.yaml().getInt("rtp.maximum-radius", 5000));
        RtpLocationFinder.BlockPosition position = this.locationFinder.randomPosition(world, minimum, maximum,
            this.configFile.yaml().getBoolean("rtp.use-world-border-center", true));
        int x = position.x();
        int z = position.z();
        world.getChunkAtAsync(x >> 4, z >> 4, true, true, chunk -> {
            if (this.pendingRtpSearches.get(player.getUniqueId()) != search || search.cancelled()) return;
            Location safe = this.locationFinder.safeLocation(world, x, z,
                this.configFile.yaml().getInt("rtp.nether-minimum-y", 8),
                this.configFile.yaml().getInt("rtp.nether-maximum-y", 120));
            if (safe == null) {
                this.findRandomLocationAsync(player, world, search, attempt + 1, maximumAttempts);
                return;
            }
            this.finishRandomTeleport(player, safe, search);
        });
    }

    private void finishRandomTeleport(final Player player, final Location destination, final RtpSearch search) {
        long elapsed = System.currentTimeMillis() - search.startedAtMillis();
        long remainingMillis = Math.max(0L, search.delaySeconds() * 1000L - elapsed);
        long remainingTicks = (remainingMillis + 49L) / 50L;
        BukkitTask task = Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (!this.pendingRtpSearches.remove(player.getUniqueId(), search) || search.cancelled() || !player.isOnline()) return;
            this.backLocations.put(player.getUniqueId(), SavedLocation.from(player.getLocation()));
            if (player.teleport(destination)) {
                this.rtpCooldowns.put(player.getUniqueId(), Instant.now().getEpochSecond());
                this.saveData();
                this.plugin.messages().send(player, "teleport.completed");
            }
        }, remainingTicks);
        search.setTask(task);
    }

    private void delayedTeleport(final Player player, final Location destination, final String type, final boolean saveBack) {
        this.delayedTeleport(player, destination, type, saveBack, () -> {});
    }

    // El origen y la tarea quedan asociados para cancelar por movimiento, daño o desconexión.
    private void delayedTeleport(final Player player, final Location destination, final String type,
                                 final boolean saveBack, final Runnable afterTeleport) {
        PendingTeleport previous = this.pendingTeleports.remove(player.getUniqueId());
        if (previous != null) previous.task().cancel();
        long delay = type.equals("back") ? 0L : Math.max(0L, this.configFile.yaml().getLong(type + ".delay-seconds",
            this.configFile.yaml().getLong("teleport.default-delay-seconds", 3L)));
        if (delay > 0 && (type.equals("rtp") || !player.hasPermission("emsichill.teleport.bypassdelay"))) {
            this.plugin.messages().send(player, type.equals("rtp") ? "teleport.rtp-starting" : "teleport.starting",
                "{seconds}", Long.toString(delay));
        } else delay = 0;
        Location origin = player.getLocation().clone();
        long ticks = delay * 20L;
        BukkitTask task = Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            this.pendingTeleports.remove(player.getUniqueId());
            if (!player.isOnline()) return;
            if (saveBack) this.backLocations.put(player.getUniqueId(), SavedLocation.from(player.getLocation()));
            player.teleport(destination);
            afterTeleport.run();
            this.plugin.messages().send(player, "teleport.completed");
        }, ticks);
        this.pendingTeleports.put(player.getUniqueId(), new PendingTeleport(origin, task));
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(final PlayerMoveEvent event) {
        if (!this.configFile.yaml().getBoolean("teleport.cancel-on-move", true) || !event.hasChangedPosition()) return;
        RtpSearch search = this.pendingRtpSearches.get(event.getPlayer().getUniqueId());
        if (search != null && search.origin().getWorld().equals(event.getTo().getWorld())
            && search.origin().distanceSquared(event.getTo()) >= 0.01) {
            this.pendingRtpSearches.remove(event.getPlayer().getUniqueId(), search);
            search.cancel();
            this.plugin.messages().send(event.getPlayer(), "teleport.cancelled-move");
        }
        PendingTeleport pending = this.pendingTeleports.get(event.getPlayer().getUniqueId());
        if (pending == null || pending.origin().distanceSquared(event.getTo()) < 0.01) return;
        this.pendingTeleports.remove(event.getPlayer().getUniqueId());
        pending.task().cancel();
        this.plugin.messages().send(event.getPlayer(), "teleport.cancelled-move");
    }

    @EventHandler
    public void onDeath(final PlayerDeathEvent event) {
        if (this.configFile.yaml().getBoolean("back.save-death-location", true)) {
            this.backLocations.put(event.getEntity().getUniqueId(), SavedLocation.from(event.getEntity().getLocation()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(final EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)
            || !this.configFile.yaml().getBoolean("teleport.cancel-on-damage", true)) return;
        PendingTeleport pending = this.pendingTeleports.remove(player.getUniqueId());
        if (pending != null) {
            pending.task().cancel();
            this.plugin.messages().send(player, "teleport.cancelled-damage");
        }
        RtpSearch search = this.pendingRtpSearches.remove(player.getUniqueId());
        if (search != null) {
            search.cancel();
            this.plugin.messages().send(player, "teleport.cancelled-damage");
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        PendingTeleport pending = this.pendingTeleports.remove(event.getPlayer().getUniqueId());
        if (pending != null) pending.task().cancel();
        RtpSearch search = this.pendingRtpSearches.remove(event.getPlayer().getUniqueId());
        if (search != null) search.cancel();
        this.incomingRequests.remove(event.getPlayer().getUniqueId());
        this.incomingRequests.entrySet().removeIf(entry -> entry.getValue().requester().equals(event.getPlayer().getUniqueId()));
    }

    private Player findOnline(final String name) {
        for (Player player : Bukkit.getOnlinePlayers()) if (player.getName().equalsIgnoreCase(name)) return player;
        return null;
    }

    private String userKey(final Player player) { return player.getName().toLowerCase(Locale.ROOT); }

    // Homes y preferencias se reconstruyen tolerando mundos que aún no estén cargados.
    private void loadData() {
        if (this.dataFile.exists()) {
            YamlConfiguration teleportYaml = this.plugin.dataStore().load(this.dataFile);
            ConfigurationSection players = teleportYaml.getConfigurationSection("players");
            if (players != null) {
                for (String playerKey : players.getKeys(false)) {
                    String key = playerKey.toLowerCase(Locale.ROOT);
                    this.playerNames.put(key, teleportYaml.getString("players." + playerKey + ".last-name", playerKey));
                    if (teleportYaml.getBoolean("players." + playerKey + ".tpa-disabled")) this.tpaDisabled.add(key);
                }
            }
            ConfigurationSection cooldowns = teleportYaml.getConfigurationSection("rtp-cooldowns");
            long now = Instant.now().getEpochSecond();
            long duration = Math.max(0L, this.configFile.yaml().getLong("rtp.cooldown-seconds", 1800L));
            if (cooldowns != null) {
                for (String value : cooldowns.getKeys(false)) {
                    try {
                        long startedAt = cooldowns.getLong(value);
                        if (startedAt + duration > now) this.rtpCooldowns.put(UUID.fromString(value), startedAt);
                    } catch (IllegalArgumentException exception) {
                        this.plugin.getLogger().warning("Se ignoró un cooldown de RTP con UUID inválido: " + value);
                    }
                }
            }
        }
    }

    private void saveData() {
        this.saveTeleportData();
    }

    private void saveTeleportData() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (String playerKey : this.tpaDisabled) {
            yaml.set("players." + playerKey + ".last-name", this.playerNames.getOrDefault(playerKey, playerKey));
            yaml.set("players." + playerKey + ".tpa-disabled", true);
        }
        long now = Instant.now().getEpochSecond();
        long duration = Math.max(0L, this.configFile.yaml().getLong("rtp.cooldown-seconds", 1800L));
        for (Map.Entry<UUID, Long> entry : this.rtpCooldowns.entrySet()) {
            if (entry.getValue() + duration > now) {
                yaml.set("rtp-cooldowns." + entry.getKey(), entry.getValue());
            }
        }
        this.saveYaml(this.dataFile, yaml, "Teleport/data.yml");
    }

    private void saveYaml(final File file, final YamlConfiguration yaml, final String displayName) {
        this.plugin.dataStore().saveAsync(file, yaml);
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args) {
        if (!(sender instanceof Player player)) return Collections.emptyList();
        if (command.getName().equalsIgnoreCase("back") && args.length == 1
            && player.hasPermission("emsichill.back.others")) {
            List<String> names = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) names.add(online.getName());
            return CommandSuggestions.filter(names, args[0]);
        }
        if (args.length != 1) return Collections.emptyList();
        if (command.getName().equalsIgnoreCase("tpa") || command.getName().equalsIgnoreCase("tpahere")) {
            List<String> names = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) if (!online.equals(player)) names.add(online.getName());
            return CommandSuggestions.filter(names, args[0]);
        }
        return Collections.emptyList();
    }

}
