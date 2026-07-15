package me.jaime.emsichill.social;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.scheduler.BukkitTask;

import me.jaime.emsichill.Main;
import me.jaime.emsichill.config.ConfigFile;

/** Gestiona posturas sociales, /whereami y avisos relacionados con dormir. */
public final class SocialManager implements CommandExecutor, Listener {
    private final Main plugin;
    private final Set<UUID> sittingPlayers = new HashSet<>();
    private final Map<UUID, ArmorStand> seats = new HashMap<>();
    private ConfigFile configFile;
    private BukkitTask seatTask;

    public SocialManager(final Main plugin) {
        this.plugin = plugin;
        this.reloadConfiguration();
    }

    public void reloadConfiguration() {
        if (this.configFile == null) this.configFile = new ConfigFile(this.plugin, "Social/config.yml");
        else this.configFile.reload();
        if (!this.configFile.yaml().getBoolean("poses.enabled", true)) this.stop();
    }

    public void start() {
        if (this.seatTask != null) this.seatTask.cancel();
        this.seatTask = Bukkit.getScheduler().runTaskTimer(this.plugin, this::maintainSeats, 1L, 5L);
    }

    public void stop() {
        if (this.seatTask != null) {
            this.seatTask.cancel();
            this.seatTask = null;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (this.sittingPlayers.contains(player.getUniqueId())) this.clearSit(player);
        }
        for (ArmorStand seat : this.seats.values()) if (seat.isValid()) seat.remove();
        this.seats.clear();
        this.sittingPlayers.clear();
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (!this.plugin.moduleEnabled("social")) {
            this.plugin.messages().send(sender, "general.module-disabled");
            return true;
        }
        if (!(sender instanceof Player player)) {
            this.plugin.messages().send(sender, "general.only-players");
            return true;
        }
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "sit" -> this.toggleSit(player);
            case "stand" -> this.stand(player);
            case "whereami" -> this.shareLocation(player);
            default -> true;
        };
    }

    private boolean toggleSit(final Player player) {
        if (!this.configFile.yaml().getBoolean("poses.enabled", true)) {
            this.plugin.messages().send(player, "general.module-disabled");
            return true;
        }
        if (!player.hasPermission("emsichill.pose")) {
            this.plugin.messages().send(player, "general.no-permission");
            return true;
        }
        if (!player.isOnGround()) {
            this.plugin.messages().send(player, "social.must-be-on-ground");
            return true;
        }
        if (this.sittingPlayers.contains(player.getUniqueId())) return this.stand(player);

        this.clearSit(player);
        if (!this.createSeat(player)) {
            this.plugin.messages().send(player, "social.pose-error");
            return true;
        }
        this.sittingPlayers.add(player.getUniqueId());
        return true;
    }

    private boolean stand(final Player player) {
        this.clearSit(player);
        return true;
    }

    private void clearSit(final Player player) {
        this.sittingPlayers.remove(player.getUniqueId());
        ArmorStand seat = this.seats.remove(player.getUniqueId());
        if (seat != null) {
            if (player.getVehicle() != null && player.getVehicle().getUniqueId().equals(seat.getUniqueId())) {
                player.leaveVehicle();
            }
            if (seat.isValid()) seat.remove();
        }
        player.setPose(Pose.STANDING, false);
    }

    // El asiento invisible usa la animacion vanilla de pasajero y se elimina al levantarse.
    private boolean createSeat(final Player player) {
        org.bukkit.Location seatLocation = player.getLocation().clone().subtract(0.0, 1.7, 0.0);
        ArmorStand seat = player.getWorld().spawn(seatLocation, ArmorStand.class, armorStand -> {
            armorStand.setInvisible(true);
            armorStand.setGravity(false);
            armorStand.setInvulnerable(true);
            armorStand.setSilent(true);
            armorStand.setPersistent(false);
            armorStand.setBasePlate(false);
            armorStand.setArms(false);
            armorStand.setCollidable(false);
        });
        if (!seat.addPassenger(player)) {
            seat.remove();
            return false;
        }
        this.seats.put(player.getUniqueId(), seat);
        return true;
    }

    // Comprueba que el asiento temporal siga existiendo mientras el jugador esta sentado.
    private void maintainSeats() {
        for (UUID playerId : new HashSet<>(this.sittingPlayers)) {
            Player player = Bukkit.getPlayer(playerId);
            ArmorStand seat = this.seats.get(playerId);
            if (player == null || !player.isOnline() || player.isDead()
                || seat == null || !seat.isValid() || !seat.getPassengers().contains(player)) {
                if (player != null) this.clearSit(player);
                else {
                    if (seat != null && seat.isValid()) seat.remove();
                    this.seats.remove(playerId);
                    this.sittingPlayers.remove(playerId);
                }
            }
        }
    }

    private boolean shareLocation(final Player player) {
        if (!this.configFile.yaml().getBoolean("whereami.enabled", true)) {
            this.plugin.messages().send(player, "general.module-disabled");
            return true;
        }
        if (!player.hasPermission("emsichill.whereami")) {
            this.plugin.messages().send(player, "general.no-permission");
            return true;
        }
        Bukkit.broadcast(this.plugin.messages().component("social.whereami", "{player}", player.getName(),
            "{dimension}", this.dimensionName(player.getWorld()), "{world}", player.getWorld().getName(),
            "{x}", Integer.toString(player.getLocation().getBlockX()),
            "{y}", Integer.toString(player.getLocation().getBlockY()),
            "{z}", Integer.toString(player.getLocation().getBlockZ())));
        return true;
    }

    private String dimensionName(final World world) {
        return switch (world.getEnvironment()) {
            case NORMAL -> "Mundo normal";
            case NETHER -> "Nether";
            case THE_END -> "End";
            default -> world.getName();
        };
    }

    @EventHandler(ignoreCancelled = true)
    public void onSneak(final PlayerToggleSneakEvent event) {
        if (this.sittingPlayers.contains(event.getPlayer().getUniqueId())) this.clearSit(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(final PlayerTeleportEvent event) {
        if (this.sittingPlayers.contains(event.getPlayer().getUniqueId())) this.clearSit(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(final EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && this.sittingPlayers.contains(player.getUniqueId())) {
            this.clearSit(player);
        }
    }

    @EventHandler
    public void onDeath(final PlayerDeathEvent event) {
        if (this.sittingPlayers.contains(event.getEntity().getUniqueId())) this.clearSit(event.getEntity());
    }

    @EventHandler
    public void onRespawn(final PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTask(this.plugin, () -> this.clearSit(event.getPlayer()));
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        if (this.sittingPlayers.contains(event.getPlayer().getUniqueId())) this.clearSit(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBedEnter(final PlayerBedEnterEvent event) {
        if (!this.plugin.moduleEnabled("social")
            || !this.configFile.yaml().getBoolean("sleep-announcements.enabled", true)) return;
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (player.isOnline() && player.isSleeping() && !this.sittingPlayers.contains(player.getUniqueId())) {
                Bukkit.broadcast(this.plugin.messages().component("social.went-to-sleep", "{player}", player.getName()));
            }
        });
    }
}