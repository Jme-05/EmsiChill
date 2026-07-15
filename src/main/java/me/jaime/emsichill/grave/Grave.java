package me.jaime.emsichill.grave;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

/** Modelo persistente de una tumba, incluidos objetos, experiencia, ubicación y protección. */
final class Grave {
    private final String id;
    private final UUID owner;
    private final String ownerName;
    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private final List<ItemStack> items;
    private int experience;
    private final long createdAt;
    private final long expiresAt;
    private final long publicAt;
    private final boolean marker;

    Grave(
        final String id,
        final UUID owner,
        final String ownerName,
        final Location location,
        final List<ItemStack> items,
        final int experience,
        final long createdAt,
        final long expiresAt,
        final long publicAt,
        final boolean marker
    ) {
        this.id = id;
        this.owner = owner;
        this.ownerName = ownerName;
        this.world = location.getWorld().getName();
        this.x = location.getBlockX();
        this.y = location.getBlockY();
        this.z = location.getBlockZ();
        this.items = items;
        this.experience = experience;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.publicAt = publicAt;
        this.marker = marker;
    }

    Location location() {
        World loaded = Bukkit.getWorld(this.world);
        return loaded == null ? null : new Location(loaded, this.x, this.y, this.z);
    }

    void save(final YamlConfiguration yaml, final String path) {
        yaml.set(path + ".owner", this.owner.toString());
        yaml.set(path + ".owner-name", this.ownerName);
        yaml.set(path + ".world", this.world);
        yaml.set(path + ".x", this.x);
        yaml.set(path + ".y", this.y);
        yaml.set(path + ".z", this.z);
        yaml.set(path + ".items", this.items);
        yaml.set(path + ".experience", this.experience);
        yaml.set(path + ".created-at", this.createdAt);
        yaml.set(path + ".expires-at", this.expiresAt);
        yaml.set(path + ".public-at", this.publicAt);
        yaml.set(path + ".marker", this.marker);
    }

    @SuppressWarnings("unchecked")
    static Grave load(final YamlConfiguration yaml, final String path, final String id) {
        try {
            String worldName = yaml.getString(path + ".world");
            World world = worldName == null ? null : Bukkit.getWorld(worldName);
            if (world == null) {
                return null;
            }
            List<ItemStack> items = new ArrayList<>((List<ItemStack>) (List<?>)
                yaml.getList(path + ".items", Collections.emptyList()));
            Location location = new Location(world, yaml.getInt(path + ".x"), yaml.getInt(path + ".y"),
                yaml.getInt(path + ".z"));
            return new Grave(id, UUID.fromString(yaml.getString(path + ".owner", "")),
                yaml.getString(path + ".owner-name", "unknown"), location, items,
                yaml.getInt(path + ".experience"), yaml.getLong(path + ".created-at"),
                yaml.getLong(path + ".expires-at"), yaml.getLong(path + ".public-at"),
                yaml.getBoolean(path + ".marker"));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    String id() { return this.id; }
    UUID owner() { return this.owner; }
    String ownerName() { return this.ownerName; }
    String world() { return this.world; }
    int x() { return this.x; }
    int y() { return this.y; }
    int z() { return this.z; }
    List<ItemStack> items() { return this.items; }
    int experience() { return this.experience; }
    void setExperience(final int value) { this.experience = value; }
    long createdAt() { return this.createdAt; }
    long expiresAt() { return this.expiresAt; }
    long publicAt() { return this.publicAt; }
    boolean hasMarker() { return this.marker; }
}