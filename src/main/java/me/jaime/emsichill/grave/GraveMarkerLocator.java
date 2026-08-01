package me.jaime.emsichill.grave;

import java.util.function.Predicate;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/** Finds a visible grave marker without destroying occupied blocks. */
final class GraveMarkerLocator {
    private GraveMarkerLocator() {
    }

    static Location find(final Location origin, final int maximumRadius, final int verticalRadius) {
        return find(origin, maximumRadius, verticalRadius, Material::isSolid);
    }

    static Location find(final Location origin, final int maximumRadius, final int verticalRadius,
                         final Predicate<Material> solidMaterial) {
        World world = origin.getWorld();
        if (world == null) return null;
        Location nearby = findSupported(world, origin, maximumRadius, verticalRadius, solidMaterial);
        if (nearby != null) return nearby;

        Location below = findSupportedBelow(world, origin, maximumRadius, solidMaterial);
        if (below != null) return below;

        Location floating = findFloating(world, origin, maximumRadius, verticalRadius);
        if (floating != null) return floating;

        Location spawn = world.getSpawnLocation();
        Location spawnMarker = findSupported(world, spawn, maximumRadius, verticalRadius, solidMaterial);
        if (spawnMarker != null) return spawnMarker;
        return findFloating(world, spawn, maximumRadius, verticalRadius);
    }

    private static Location findSupported(final World world, final Location origin, final int maximumRadius,
                                          final int verticalRadius, final Predicate<Material> solidMaterial) {
        for (int radius = 0; radius <= maximumRadius; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (!onSearchRing(radius, x, z)) continue;
                    int bx = origin.getBlockX() + x;
                    int bz = origin.getBlockZ() + z;
                    for (int distance = 0; distance <= verticalRadius; distance++) {
                        int[] offsets = distance == 0 ? new int[] {0} : new int[] {distance, -distance};
                        for (int yOffset : offsets) {
                            int by = origin.getBlockY() + yOffset;
                            if (by <= world.getMinHeight() || by >= world.getMaxHeight() - 1) continue;
                            Block target = world.getBlockAt(bx, by, bz);
                            if (canPlaceSupported(target, solidMaterial)) return target.getLocation();
                        }
                    }
                }
            }
        }
        return null;
    }

    private static Location findSupportedBelow(final World world, final Location origin, final int maximumRadius,
                                               final Predicate<Material> solidMaterial) {
        int startY = Math.min(world.getMaxHeight() - 3,
            Math.max(world.getMinHeight() + 1, origin.getBlockY()));
        for (int radius = 0; radius <= maximumRadius; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (!onSearchRing(radius, x, z)) continue;
                    int bx = origin.getBlockX() + x;
                    int bz = origin.getBlockZ() + z;
                    for (int by = startY; by > world.getMinHeight(); by--) {
                        Block target = world.getBlockAt(bx, by, bz);
                        if (canPlaceSupported(target, solidMaterial)) return target.getLocation();
                    }
                }
            }
        }
        return null;
    }

    private static Location findFloating(final World world, final Location origin, final int maximumRadius,
                                         final int verticalRadius) {
        int originY = Math.min(world.getMaxHeight() - 3,
            Math.max(world.getMinHeight() + 1, origin.getBlockY()));
        for (int radius = 0; radius <= maximumRadius; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (!onSearchRing(radius, x, z)) continue;
                    for (int distance = 0; distance <= verticalRadius; distance++) {
                        int[] offsets = distance == 0 ? new int[] {0} : new int[] {-distance, distance};
                        for (int offset : offsets) {
                            int y = originY + offset;
                            if (y <= world.getMinHeight() || y >= world.getMaxHeight() - 2) continue;
                            Block target = world.getBlockAt(origin.getBlockX() + x, y, origin.getBlockZ() + z);
                            if (canPlaceFloating(target)) return target.getLocation();
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean onSearchRing(final int radius, final int x, final int z) {
        return radius == 0 || Math.abs(x) == radius || Math.abs(z) == radius;
    }

    private static boolean canPlaceSupported(final Block target, final Predicate<Material> solidMaterial) {
        Block above = target.getRelative(0, 1, 0);
        Block head = target.getRelative(0, 2, 0);
        Material floorType = target.getRelative(0, -1, 0).getType();
        return solidMaterial.test(floorType) && floorType != Material.MAGMA_BLOCK && floorType != Material.CACTUS
            && floorType != Material.CAMPFIRE && floorType != Material.SOUL_CAMPFIRE
            && hasClearMarkerSpace(target, above, head);
    }

    private static boolean canPlaceFloating(final Block target) {
        return hasClearMarkerSpace(target, target.getRelative(0, 1, 0), target.getRelative(0, 2, 0));
    }

    private static boolean hasClearMarkerSpace(final Block target, final Block above, final Block head) {
        return target.isPassable() && !target.isLiquid() && above.isPassable() && !above.isLiquid()
            && head.isPassable() && !head.isLiquid()
            && target.getWorld().getWorldBorder().isInside(target.getLocation());
    }
}
