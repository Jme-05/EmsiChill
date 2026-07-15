package me.jaime.emsichill.teleport;

import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;

/** Teletransporte retrasado junto a su origen y tarea cancelable. */
public record PendingTeleport(Location origin, BukkitTask task) {
}