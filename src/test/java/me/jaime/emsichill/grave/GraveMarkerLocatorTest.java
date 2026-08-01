package me.jaime.emsichill.grave;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

class GraveMarkerLocatorTest {
    @Test
    void findsGroundFarBelowAnAirDeath() {
        FakeWorld fake = new FakeWorld(-64, 320, 64);

        Location marker = GraveMarkerLocator.find(new Location(fake.world(), 0, 200, 0), 0, 4,
            material -> material == Material.STONE);

        assertNotNull(marker);
        assertEquals(65, marker.getBlockY());
    }

    @Test
    void createsAFloatingMarkerWhenTheColumnHasNoGround() {
        FakeWorld fake = new FakeWorld(-64, 320, -128);

        Location marker = GraveMarkerLocator.find(new Location(fake.world(), 3, 200, -7), 0, 4,
            material -> material == Material.STONE);

        assertNotNull(marker);
        assertEquals(3, marker.getBlockX());
        assertEquals(200, marker.getBlockY());
        assertEquals(-7, marker.getBlockZ());
    }

    private static final class FakeWorld {
        private final int minimumHeight;
        private final int maximumHeight;
        private final int groundY;
        private final Map<BlockKey, Block> blocks = new HashMap<>();
        private final WorldBorder border;
        private final World world;

        private FakeWorld(final int minimumHeight, final int maximumHeight, final int groundY) {
            this.minimumHeight = minimumHeight;
            this.maximumHeight = maximumHeight;
            this.groundY = groundY;
            this.border = (WorldBorder) Proxy.newProxyInstance(WorldBorder.class.getClassLoader(),
                new Class<?>[] {WorldBorder.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("isInside")) return true;
                    return defaultValue(proxy, method, arguments);
                });
            this.world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class},
                (proxy, method, arguments) -> this.worldCall(proxy, method, arguments));
        }

        private World world() {
            return this.world;
        }

        private Object worldCall(final Object proxy, final Method method, final Object[] arguments) {
            return switch (method.getName()) {
                case "getMinHeight" -> this.minimumHeight;
                case "getMaxHeight" -> this.maximumHeight;
                case "getWorldBorder" -> this.border;
                case "getName" -> "grave-test";
                case "getUID" -> new UUID(0L, 1L);
                case "getSpawnLocation" -> new Location(this.world, 0, Math.max(this.groundY + 1, 64), 0);
                case "getBlockAt" -> this.block((int) arguments[0], (int) arguments[1], (int) arguments[2]);
                default -> defaultValue(proxy, method, arguments);
            };
        }

        private Block block(final int x, final int y, final int z) {
            return this.blocks.computeIfAbsent(new BlockKey(x, y, z), key -> (Block) Proxy.newProxyInstance(
                Block.class.getClassLoader(), new Class<?>[] {Block.class},
                (proxy, method, arguments) -> this.blockCall(proxy, method, arguments, key)));
        }

        private Object blockCall(final Object proxy, final Method method, final Object[] arguments,
                                 final BlockKey key) {
            return switch (method.getName()) {
                case "getRelative" -> this.block(key.x() + (int) arguments[0], key.y() + (int) arguments[1],
                    key.z() + (int) arguments[2]);
                case "getType" -> key.y() <= this.groundY ? Material.STONE : Material.AIR;
                case "isPassable" -> key.y() > this.groundY;
                case "isLiquid" -> false;
                case "getWorld" -> this.world;
                case "getLocation" -> new Location(this.world, key.x(), key.y(), key.z());
                case "getX" -> key.x();
                case "getY" -> key.y();
                case "getZ" -> key.z();
                default -> defaultValue(proxy, method, arguments);
            };
        }

        private static Object defaultValue(final Object proxy, final Method method, final Object[] arguments) {
            return switch (method.getName()) {
                case "toString" -> "Fake" + proxy.getClass().getInterfaces()[0].getSimpleName();
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == arguments[0];
                default -> primitiveDefault(method.getReturnType());
            };
        }

        private static Object primitiveDefault(final Class<?> type) {
            if (!type.isPrimitive()) return null;
            if (type == boolean.class) return false;
            if (type == char.class) return '\0';
            if (type == byte.class) return (byte) 0;
            if (type == short.class) return (short) 0;
            if (type == int.class) return 0;
            if (type == long.class) return 0L;
            if (type == float.class) return 0.0F;
            if (type == double.class) return 0.0D;
            return null;
        }
    }

    private record BlockKey(int x, int y, int z) {
    }
}
