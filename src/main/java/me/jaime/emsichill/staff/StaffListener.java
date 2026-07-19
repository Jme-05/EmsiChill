package me.jaime.emsichill.staff;

import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import me.jaime.emsichill.Main;

/** Traduce eventos de Paper en operaciones de los servicios de moderación. */
public final class StaffListener implements Listener {
    private static final List<String> FROZEN_COMMANDS = List.of("login", "register", "freeze");

    private final Main plugin;
    private final StaffService staff;
    private final InspectionService inspections;
    private final FreezeService freezes;
    private final ModerationService moderation;

    public StaffListener(
        final Main plugin,
        final StaffService staff,
        final InspectionService inspections,
        final FreezeService freezes,
        final ModerationService moderation
    ) {
        this.plugin = plugin;
        this.staff = staff;
        this.inspections = inspections;
        this.freezes = freezes;
        this.moderation = moderation;
    }

    @EventHandler(ignoreCancelled = true)
    public void onToolUse(final PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!this.staff.isStaffMode(player)) return;
        String id = this.staff.toolId(event.getItem());
        if (id == null) return;
        event.setCancelled(true);
        if (id.equals("vanish")) {
            boolean enabled = this.staff.toggleVanish(player);
            this.plugin.messages().send(player, enabled ? "staff.vanish-on" : "staff.vanish-off");
        } else if (id.equals("random")) {
            Player target = this.staff.randomVisiblePlayer(player);
            if (target == null) this.plugin.messages().send(player, "staff.random-none");
            else player.teleport(target.getLocation());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInspect(final PlayerInteractEntityEvent event) {
        Player viewer = event.getPlayer();
        if (!(event.getRightClicked() instanceof Player target) || !this.staff.isStaffMode(viewer)) return;
        String id = this.staff.toolId(viewer.getInventory().getItemInMainHand());
        InspectionService.Type type = "inventory".equals(id) ? InspectionService.Type.INVENTORY
            : "ender".equals(id) ? InspectionService.Type.ENDER_CHEST : null;
        if (type == null) return;
        event.setCancelled(true);
        InspectionService.OpenResult result = this.inspections.open(viewer, target, type);
        if (result == InspectionService.OpenResult.NO_PERMISSION) {
            this.plugin.messages().send(viewer, "general.no-permission");
            return;
        }
        String mode = result == InspectionService.OpenResult.OPENED_EDITABLE ? "editable" : "read-only";
        this.plugin.messages().send(viewer, "staff.inspection-opened-" + mode,
            "{player}", target.getName());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (this.freezes.isFrozen(player.getUniqueId()) || this.inspections.isReadOnly(player, event.getView())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (this.freezes.isFrozen(player.getUniqueId()) || this.inspections.isReadOnly(player, event.getView())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(final InventoryCloseEvent event) {
        this.inspections.close(event.getPlayer().getUniqueId());
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMutedChat(final AsyncPlayerChatEvent event) {
        MuteRecord mute = this.moderation.activeMute(event.getPlayer().getName());
        if (mute == null) return;
        event.setCancelled(true);
        Bukkit.getScheduler().runTask(this.plugin, () -> this.sendMuteStatus(event.getPlayer(), mute));
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(final AsyncPlayerChatEvent event) {
        if (!this.staff.usesStaffChat(event.getPlayer())) return;
        event.setCancelled(true);
        Bukkit.getScheduler().runTask(this.plugin,
            () -> this.staff.sendStaffMessage(event.getPlayer(), event.getMessage()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onMobTarget(final EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player player && this.staff.isVanished(player)) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(final EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player
            && (this.staff.blocksItemPickup(player) || this.freezes.isFrozen(player.getUniqueId()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(final PlayerMoveEvent event) {
        if (!this.freezes.isFrozen(event.getPlayer().getUniqueId()) || event.getTo() == null) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY()
            && from.getBlockZ() == to.getBlockZ()) return;
        Location locked = from.clone();
        locked.setYaw(to.getYaw());
        locked.setPitch(to.getPitch());
        event.setTo(locked);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(final BlockBreakEvent event) {
        if (this.freezes.isFrozen(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(final BlockPlaceEvent event) {
        if (this.freezes.isFrozen(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onFrozenInteract(final PlayerInteractEvent event) {
        if (this.freezes.isFrozen(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onFrozenEntityInteract(final PlayerInteractEntityEvent event) {
        if (this.freezes.isFrozen(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(final PlayerDropItemEvent event) {
        if (this.freezes.isFrozen(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(final EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && this.freezes.isFrozen(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(final PlayerCommandPreprocessEvent event) {
        if (!this.freezes.isFrozen(event.getPlayer().getUniqueId())) return;
        String command = event.getMessage().substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        if (FROZEN_COMMANDS.contains(command)) return;
        event.setCancelled(true);
        this.plugin.messages().send(event.getPlayer(), "staff.frozen-command");
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (this.staff.recoverPlayer(event.getPlayer())) {
                this.plugin.messages().send(event.getPlayer(), "staff.staffmode-recovered");
            }
            MuteRecord mute = this.moderation.activeMute(event.getPlayer().getName());
            if (mute != null) this.sendMuteStatus(event.getPlayer(), mute);
        }, 5L);
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        this.staff.leave(event.getPlayer());
        this.freezes.release(event.getPlayer().getUniqueId());
        this.inspections.close(event.getPlayer().getUniqueId());
    }

    private void sendMuteStatus(final Player player, final MuteRecord mute) {
        if (mute.permanent()) {
            this.plugin.messages().send(player, "staff.mute-blocked-permanent");
            return;
        }
        this.plugin.messages().send(player, "staff.mute-blocked-timed", "{remaining}",
            MuteDuration.format(mute.expiresAt() - System.currentTimeMillis()));
    }
}
