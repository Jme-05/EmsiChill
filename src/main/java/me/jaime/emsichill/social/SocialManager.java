package me.jaime.emsichill.social;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
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
import org.bukkit.event.entity.EntityToggleSwimEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;

import me.jaime.emsichill.Main;
import me.jaime.emsichill.config.ConfigFile;

/** Gestiona posturas sociales, /whereami y avisos relacionados con dormir. */
public final class SocialManager implements CommandExecutor, Listener {
    private final Main plugin;
    private final PoseController poseController;
    private final Map<UUID, SocialPose> activePoses = new HashMap<>();
    private final Map<UUID, ArmorStand> seats = new HashMap<>();
    private ConfigFile configFile;
    private boolean running;

    public SocialManager(final Main plugin) {
        this.plugin = plugin;
        this.poseController = new PoseController();
        this.reloadConfiguration();
    }

    public void reloadConfiguration() {
        if (this.configFile == null) this.configFile = new ConfigFile(this.plugin, "Social/config.yml");
        else this.configFile.reload();
        if (!this.configFile.yaml().getBoolean("poses.enabled", true)) this.stop();
        else this.running = true;
    }

    public void start() {
        this.running = this.configFile.yaml().getBoolean("poses.enabled", true);
    }

    public void stop() {
        this.running = false;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (this.activePoses.containsKey(player.getUniqueId())) this.clearPose(player);
        }
        for (ArmorStand seat : this.seats.values()) if (seat.isValid()) seat.remove();
        this.seats.clear();
        this.activePoses.clear();
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
            case "sit" -> this.togglePose(player, SocialPose.SIT);
            case "crawl" -> this.togglePose(player, SocialPose.CRAWL);
            case "stand" -> this.stand(player);
            case "whereami" -> this.shareLocation(player);
            default -> true;
        };
    }

    private boolean togglePose(final Player player, final SocialPose pose) {
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
        if (this.activePoses.get(player.getUniqueId()) == pose) return this.stand(player);

        this.clearPose(player);
        if (pose == SocialPose.SIT && !this.createSeat(player)) {
            this.plugin.messages().send(player, "social.pose-error");
            return true;
        }
        if (pose == SocialPose.CRAWL && !this.poseController.apply(player)) {
            this.poseController.clear(player);
            this.plugin.messages().send(player, "social.pose-error");
            return true;
        }
        this.activePoses.put(player.getUniqueId(), pose);
        return true;
    }

    private boolean stand(final Player player) {
        this.clearPose(player);
        return true;
    }

    private void clearPose(final Player player) {
        SocialPose pose = this.activePoses.remove(player.getUniqueId());
        ArmorStand seat = this.seats.remove(player.getUniqueId());
        if (seat != null) {
            if (player.getVehicle() != null && player.getVehicle().getUniqueId().equals(seat.getUniqueId())) {
                player.leaveVehicle();
            }
            if (seat.isValid()) seat.remove();
        }
        if (pose == SocialPose.CRAWL) this.poseController.clear(player);
        else player.setPose(Pose.STANDING, false);
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

    // Conserva las poses fijas y retira cualquier asiento temporal que deje de ser valido.
    private void maintainPoses() {
        for (UUID playerId : new HashSet<>(this.activePoses.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            SocialPose pose = this.activePoses.get(playerId);
            ArmorStand seat = this.seats.get(playerId);
            boolean invalidSeat = pose == SocialPose.SIT
                && (seat == null || !seat.isValid() || !seat.getPassengers().contains(player));
            if (player == null || !player.isOnline() || player.isDead() || invalidSeat) {
                if (player != null) this.clearPose(player);
                else {
                    if (seat != null && seat.isValid()) seat.remove();
                    this.seats.remove(playerId);
                    this.activePoses.remove(playerId);
                }
                continue;
            }
            if (pose != SocialPose.SIT) {
                this.poseController.apply(player);
            }
        }
    }

    @EventHandler
    public void onTickEnd(final ServerTickEndEvent event) {
        if (this.running && !this.activePoses.isEmpty()) this.maintainPoses();
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
        if (event.isSneaking() && this.activePoses.containsKey(event.getPlayer().getUniqueId())) {
            this.clearPose(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onToggleSwim(final EntityToggleSwimEvent event) {
        if (event.getEntity() instanceof Player player
            && this.activePoses.get(player.getUniqueId()) == SocialPose.CRAWL) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(final PlayerTeleportEvent event) {
        if (this.activePoses.containsKey(event.getPlayer().getUniqueId())) this.clearPose(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(final EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && this.activePoses.containsKey(player.getUniqueId())) {
            this.clearPose(player);
        }
    }

    @EventHandler
    public void onDeath(final PlayerDeathEvent event) {
        if (this.activePoses.containsKey(event.getEntity().getUniqueId())) this.clearPose(event.getEntity());
    }

    @EventHandler
    public void onRespawn(final PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTask(this.plugin, () -> this.clearPose(event.getPlayer()));
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        if (this.activePoses.containsKey(event.getPlayer().getUniqueId())) this.clearPose(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBedEnter(final PlayerBedEnterEvent event) {
        if (!this.plugin.moduleEnabled("social")
            || !this.configFile.yaml().getBoolean("sleep-announcements.enabled", true)) return;
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (player.isOnline() && player.isSleeping() && !this.activePoses.containsKey(player.getUniqueId())) {
                Bukkit.broadcast(this.plugin.messages().component("social.went-to-sleep", "{player}", player.getName()));
            }
        });
    }

    private enum SocialPose {
        SIT(Pose.SITTING),
        CRAWL(Pose.SWIMMING);

        private final Pose bukkitPose;

        SocialPose(final Pose bukkitPose) {
            this.bukkitPose = bukkitPose;
        }

        private Pose bukkitPose() {
            return this.bukkitPose;
        }
    }
}
