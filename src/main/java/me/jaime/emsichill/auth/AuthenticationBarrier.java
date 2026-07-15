package me.jaime.emsichill.auth;

import java.util.Locale;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.projectiles.ProjectileSource;

import me.jaime.emsichill.Main;

/**
 * Impide que un jugador interactúe con el servidor hasta completar la autenticación. Las
 * acciones permitidas se consultan en la configuración mediante AuthenticationManager.
 */
public final class AuthenticationBarrier implements Listener {
    private final Main plugin;
    private final AuthenticationManager authentication;

    public AuthenticationBarrier(final Main plugin, final AuthenticationManager authentication) {
        this.plugin = plugin;
        this.authentication = authentication;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(final PlayerMoveEvent event) {
        if (!this.authentication.blocks(event.getPlayer(), "movement") || !event.hasChangedPosition()) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        event.setTo(new Location(from.getWorld(), from.getX(), from.getY(), from.getZ(), to.getYaw(), to.getPitch()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(final PlayerTeleportEvent event) {
        if (this.authentication.blocks(event.getPlayer(), "teleport")
            && !this.authentication.isInternalTeleport(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(final PlayerCommandPreprocessEvent event) {
        if (!this.authentication.blocks(event.getPlayer(), "commands")) {
            return;
        }
        String input = event.getMessage().substring(1).trim();
        String command = input.isEmpty() ? "" : input.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (this.authentication.isAllowedCommand(command)) {
            return;
        }
        event.setCancelled(true);
        this.plugin.messages().send(event.getPlayer(), "auth.commands-blocked");
    }

    @EventHandler
    public void onCommandSend(final PlayerCommandSendEvent event) {
        if (!this.authentication.shouldFilterCommandSuggestions(event.getPlayer())) {
            return;
        }
        event.getCommands().removeIf(command -> !this.authentication.isAllowedCommand(command));
    }

    @SuppressWarnings("deprecation")
    @EventHandler(ignoreCancelled = true)
    public void onChat(final AsyncPlayerChatEvent event) {
        if (this.authentication.blocks(event.getPlayer(), "chat")) {
            event.setCancelled(true);
            this.plugin.messages().send(event.getPlayer(), "auth.chat-blocked");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(final PlayerInteractEvent event) {
        if (this.authentication.blocks(event.getPlayer(), "interaction")) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(final BlockBreakEvent event) {
        if (this.authentication.blocks(event.getPlayer(), "interaction")) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(final BlockPlaceEvent event) {
        if (this.authentication.blocks(event.getPlayer(), "interaction")) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && this.authentication.blocks(player, "inventory")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && this.authentication.blocks(player, "inventory")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(final PlayerDropItemEvent event) {
        if (this.authentication.blocks(event.getPlayer(), "item-drop")) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(final EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && this.authentication.blocks(player, "item-pickup")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwapHands(final PlayerSwapHandItemsEvent event) {
        if (this.authentication.blocks(event.getPlayer(), "inventory")) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(final EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && this.authentication.blocks(player, "damage")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamageByEntity(final EntityDamageByEntityEvent event) {
        Player attacker = attacker(event.getDamager());
        if (attacker != null && this.authentication.blocks(attacker, "damage")) {
            event.setCancelled(true);
        }
    }

    private static Player attacker(final Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }
}