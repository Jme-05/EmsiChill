package me.jaime.emsichill.region;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Modelo de una región rectangular de altura ilimitada. Conserva propietarios, miembros,
 * límites horizontales y ajustes de acceso.
 */
final class Region {
    private final String id;
    private final String name;
    private UUID primaryOwner;
    private String ownerName;
    private final String world;
    private int minX;
    private int maxX;
    private int minZ;
    private int maxZ;
    private int radius;
    private final int centerY;
    private final Set<UUID> coOwners = new HashSet<>();
    private final Set<UUID> members = new HashSet<>();
    private boolean pvp;
    private boolean publicContainers;
    private boolean publicInteractions;

    Region(
        final String id,
        final String name,
        final UUID primaryOwner,
        final String ownerName,
        final String world,
        final int minX,
        final int maxX,
        final int minZ,
        final int maxZ,
        final int radius,
        final int centerY
    ) {
        this.id = id;
        this.name = name;
        this.primaryOwner = primaryOwner;
        this.ownerName = ownerName;
        this.world = world;
        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
        this.radius = radius;
        this.centerY = centerY;
    }

    boolean contains(final Location location) {
        return location.getWorld() != null && location.getWorld().getName().equals(this.world)
            && location.getBlockX() >= this.minX && location.getBlockX() <= this.maxX
            && location.getBlockZ() >= this.minZ && location.getBlockZ() <= this.maxZ;
    }

    boolean overlaps(final Region other) {
        return this.world.equals(other.world) && this.minX <= other.maxX && this.maxX >= other.minX
            && this.minZ <= other.maxZ && this.maxZ >= other.minZ;
    }

    boolean isWithinDistance(final Region other, final int distance) {
        return this.world.equals(other.world)
            && (long) this.minX - distance <= other.maxX && (long) this.maxX + distance >= other.minX
            && (long) this.minZ - distance <= other.maxZ && (long) this.maxZ + distance >= other.minZ;
    }

    Region expanded(final int newRadius) {
        Region result = new Region(this.id, this.name, this.primaryOwner, this.ownerName, this.world,
            this.centerX() - newRadius, this.centerX() + newRadius,
            this.centerZ() - newRadius, this.centerZ() + newRadius, newRadius, this.centerY);
        result.coOwners.addAll(this.coOwners);
        result.members.addAll(this.members);
        result.pvp = this.pvp;
        result.publicContainers = this.publicContainers;
        result.publicInteractions = this.publicInteractions;
        return result;
    }

    void resize(final Region expanded) {
        this.minX = expanded.minX;
        this.maxX = expanded.maxX;
        this.minZ = expanded.minZ;
        this.maxZ = expanded.maxZ;
        this.radius = expanded.radius;
    }

    void save(final YamlConfiguration yaml, final String path) {
        yaml.set(path + ".name", this.name);
        yaml.set(path + ".primary-owner", this.primaryOwner.toString());
        yaml.set(path + ".owner-name", this.ownerName);
        yaml.set(path + ".world", this.world);
        yaml.set(path + ".min-x", this.minX);
        yaml.set(path + ".max-x", this.maxX);
        yaml.set(path + ".min-z", this.minZ);
        yaml.set(path + ".max-z", this.maxZ);
        yaml.set(path + ".radius", this.radius);
        yaml.set(path + ".center-y", this.centerY);
        yaml.set(path + ".measurement", "radius-from-center");
        yaml.set(path + ".co-owners", this.coOwners.stream().map(UUID::toString).toList());
        yaml.set(path + ".members", this.members.stream().map(UUID::toString).toList());
        yaml.set(path + ".settings.pvp", this.pvp);
        yaml.set(path + ".settings.public-containers", this.publicContainers);
        yaml.set(path + ".settings.public-interactions", this.publicInteractions);
    }

    static Region load(final YamlConfiguration yaml, final String path, final String id) {
        try {
            String name = yaml.getString(path + ".name");
            String world = yaml.getString(path + ".world");
            UUID owner = UUID.fromString(yaml.getString(path + ".primary-owner", ""));
            if (name == null || world == null) {
                return null;
            }
            int minX = yaml.getInt(path + ".min-x");
            int maxX = yaml.getInt(path + ".max-x");
            int minZ = yaml.getInt(path + ".min-z");
            int maxZ = yaml.getInt(path + ".max-z");
            int radius = Math.max(1, yaml.getInt(path + (yaml.contains(path + ".radius") ? ".radius" : ".size"), 32));
            if (!yaml.contains(path + ".radius")) {
                int centerX = (minX + maxX + 1) / 2;
                int centerZ = (minZ + maxZ + 1) / 2;
                minX = centerX - radius;
                maxX = centerX + radius;
                minZ = centerZ - radius;
                maxZ = centerZ + radius;
            }
            Region region = new Region(id, name, owner, yaml.getString(path + ".owner-name", "unknown"), world,
                minX, maxX, minZ, maxZ, radius, yaml.getInt(path + ".center-y", 64));
            for (String value : yaml.getStringList(path + ".co-owners")) {
                region.coOwners.add(UUID.fromString(value));
            }
            for (String value : yaml.getStringList(path + ".members")) {
                region.members.add(UUID.fromString(value));
            }
            region.pvp = yaml.getBoolean(path + ".settings.pvp");
            region.publicContainers = yaml.getBoolean(path + ".settings.public-containers");
            region.publicInteractions = yaml.getBoolean(path + ".settings.public-interactions");
            return region;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    String id() { return this.id; }
    String name() { return this.name; }
    UUID primaryOwner() { return this.primaryOwner; }
    String ownerName() { return this.ownerName; }
    String world() { return this.world; }
    int minX() { return this.minX; }
    int maxX() { return this.maxX; }
    int minZ() { return this.minZ; }
    int maxZ() { return this.maxZ; }
    int radius() { return this.radius; }
    int diameter() { return this.radius * 2 + 1; }
    int centerX() { return (this.minX + this.maxX) / 2; }
    int centerZ() { return (this.minZ + this.maxZ) / 2; }
    int centerY() { return this.centerY; }
    Set<UUID> coOwners() { return this.coOwners; }
    Set<UUID> members() { return this.members; }
    boolean pvp() { return this.pvp; }
    boolean publicContainers() { return this.publicContainers; }
    boolean publicInteractions() { return this.publicInteractions; }
    void setPrimaryOwner(final UUID owner, final String name) { this.primaryOwner = owner; this.ownerName = name; }
    void setPvp(final boolean value) { this.pvp = value; }
    void setPublicContainers(final boolean value) { this.publicContainers = value; }
    void setPublicInteractions(final boolean value) { this.publicInteractions = value; }
}