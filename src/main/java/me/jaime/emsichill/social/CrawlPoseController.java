package me.jaime.emsichill.social;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Mantiene el espacio necesario para que el cliente conserve la postura de gateo. */
final class CrawlPoseController {
    private final Map<UUID, Location> supportBlocks = new HashMap<>();

    @SuppressWarnings("deprecation")
    boolean apply(final Player player) {
        Location support = player.getLocation().getBlock().getLocation().add(0.0D, 1.0D, 0.0D);
        Location previous = this.supportBlocks.put(player.getUniqueId(), support);
        if (previous != null && !previous.equals(support)) this.restore(player, previous);

        player.sendBlockChange(support, Material.BARRIER.createBlockData());
        if (!player.isSwimming()) player.setSwimming(true);
        return true;
    }

    @SuppressWarnings("deprecation")
    void clear(final Player player) {
        Location support = this.supportBlocks.remove(player.getUniqueId());
        if (support != null) this.restore(player, support);
        if (player.isSwimming()) player.setSwimming(false);
    }

    private void restore(final Player player, final Location location) {
        if (player.isOnline() && player.getWorld().equals(location.getWorld())) {
            player.sendBlockChange(location, location.getBlock().getBlockData());
        }
    }
}
