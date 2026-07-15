package me.jaime.emsichill.teleport;

import java.util.Random;
import java.util.Set;

import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;

/** Selecciona coordenadas aleatorias y comprueba suelo, espacio, peligros y borde del mundo. */
public final class RtpLocationFinder {
    private static final Set<Material> DANGEROUS_BLOCKS = Set.of(
        Material.LAVA, Material.WATER, Material.FIRE, Material.SOUL_FIRE, Material.CACTUS,
        Material.MAGMA_BLOCK, Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.POWDER_SNOW
    );

    private final Random random = new Random();

    public BlockPosition randomPosition(
        final World world,
        final int minimumRadius,
        final int maximumRadius,
        final boolean useWorldBorderCenter
    ) {
        Location center = useWorldBorderCenter ? world.getWorldBorder().getCenter() : world.getSpawnLocation();
        double angle = this.random.nextDouble() * Math.PI * 2.0;
        double radius = Math.sqrt(this.random.nextDouble()) * (maximumRadius - minimumRadius) + minimumRadius;
        int x = (int) Math.floor(center.getX() + Math.cos(angle) * radius);
        int z = (int) Math.floor(center.getZ() + Math.sin(angle) * radius);
        return new BlockPosition(x, z);
    }

    public Location safeLocation(
        final World world,
        final int x,
        final int z,
        final int netherMinimumY,
        final int netherMaximumY
    ) {
        if (world.getEnvironment() == World.Environment.NETHER) {
            int minimumY = Math.max(world.getMinHeight() + 1, netherMinimumY);
            int maximumY = Math.min(world.getMaxHeight() - 3, netherMaximumY);
            for (int floorY = maximumY; floorY >= minimumY; floorY--) {
                Location safe = this.safeLocationAt(world, x, floorY, z, true);
                if (safe != null) {
                    return safe;
                }
            }
            return null;
        }
        int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        return this.safeLocationAt(world, x, y, z, false);
    }

    private Location safeLocationAt(
        final World world,
        final int x,
        final int floorY,
        final int z,
        final boolean rejectBedrock
    ) {
        Location result = new Location(world, x + 0.5, floorY + 1.0, z + 0.5);
        Material floor = world.getBlockAt(x, floorY, z).getType();
        Material feet = world.getBlockAt(x, floorY + 1, z).getType();
        Material head = world.getBlockAt(x, floorY + 2, z).getType();
        WorldBorder border = world.getWorldBorder();
        boolean clear = world.getBlockAt(x, floorY + 1, z).isPassable()
            && world.getBlockAt(x, floorY + 2, z).isPassable();
        return border.isInside(result) && floor.isSolid() && (!rejectBedrock || floor != Material.BEDROCK)
            && !DANGEROUS_BLOCKS.contains(floor) && !DANGEROUS_BLOCKS.contains(feet)
            && !DANGEROUS_BLOCKS.contains(head) && clear ? result : null;
    }

    public record BlockPosition(int x, int z) {
    }
}