package me.jaime.emsichill.home;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

/** Ubicación serializable de una home, incluida la orientación del jugador. */
public record HomeLocation(String world, double x, double y, double z, float yaw, float pitch) {
    public static HomeLocation from(final Location location) {
        return new HomeLocation(location.getWorld().getName(), location.getX(), location.getY(), location.getZ(),
            location.getYaw(), location.getPitch());
    }

    public Location toLocation() {
        World loaded = Bukkit.getWorld(this.world);
        return loaded == null ? null : new Location(loaded, this.x, this.y, this.z, this.yaw, this.pitch);
    }

    public void save(final YamlConfiguration yaml, final String path) {
        yaml.set(path + ".world", this.world);
        yaml.set(path + ".x", this.x);
        yaml.set(path + ".y", this.y);
        yaml.set(path + ".z", this.z);
        yaml.set(path + ".yaw", this.yaw);
        yaml.set(path + ".pitch", this.pitch);
    }

    public static HomeLocation load(final YamlConfiguration yaml, final String path) {
        String world = yaml.getString(path + ".world");
        if (world == null) {
            return null;
        }
        return new HomeLocation(world, yaml.getDouble(path + ".x"), yaml.getDouble(path + ".y"),
            yaml.getDouble(path + ".z"), (float) yaml.getDouble(path + ".yaw"),
            (float) yaml.getDouble(path + ".pitch"));
    }
}