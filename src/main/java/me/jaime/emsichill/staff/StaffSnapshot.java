package me.jaime.emsichill.staff;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Copia completa del estado que debe restaurarse al salir del modo staff o tras un reinicio. */
final class StaffSnapshot {
    private final String gameMode;
    private final Location location;
    private final ItemStack[] contents;
    private final ItemStack[] armor;
    private final int level;
    private final float experience;
    private final boolean vanishedBefore;

    private StaffSnapshot(
        final String gameMode,
        final Location location,
        final ItemStack[] contents,
        final ItemStack[] armor,
        final int level,
        final float experience,
        final boolean vanishedBefore
    ) {
        this.gameMode = gameMode;
        this.location = location;
        this.contents = contents;
        this.armor = armor;
        this.level = level;
        this.experience = experience;
        this.vanishedBefore = vanishedBefore;
    }

    static StaffSnapshot capture(final Player player, final boolean vanishedBefore) {
        return new StaffSnapshot(player.getGameMode().name(), player.getLocation().clone(),
            player.getInventory().getContents().clone(), player.getInventory().getArmorContents().clone(),
            player.getLevel(), player.getExp(), vanishedBefore);
    }

    boolean vanishedBefore() {
        return this.vanishedBefore;
    }

    void restore(final Player player) {
        player.getInventory().clear();
        player.getInventory().setContents(this.contents);
        player.getInventory().setArmorContents(this.armor);
        player.setLevel(this.level);
        player.setExp(this.experience);
        try {
            player.setGameMode(GameMode.valueOf(this.gameMode));
        } catch (IllegalArgumentException ignored) {
            player.setGameMode(GameMode.SURVIVAL);
        }
        if (this.location.getWorld() != null) {
            player.teleport(this.location);
        }
    }

    void save(final YamlConfiguration yaml, final String path) {
        yaml.set(path + ".game-mode", this.gameMode);
        yaml.set(path + ".world", this.location.getWorld().getName());
        yaml.set(path + ".x", this.location.getX());
        yaml.set(path + ".y", this.location.getY());
        yaml.set(path + ".z", this.location.getZ());
        yaml.set(path + ".yaw", this.location.getYaw());
        yaml.set(path + ".pitch", this.location.getPitch());
        yaml.set(path + ".contents", Arrays.asList(this.contents));
        yaml.set(path + ".armor", Arrays.asList(this.armor));
        yaml.set(path + ".level", this.level);
        yaml.set(path + ".experience", this.experience);
        yaml.set(path + ".vanished-before", this.vanishedBefore);
    }

    @SuppressWarnings("unchecked")
    static StaffSnapshot load(final YamlConfiguration yaml, final String path) {
        String worldName = yaml.getString(path + ".world");
        World world = worldName == null ? null : Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        List<ItemStack> contents = (List<ItemStack>) (List<?>)
            yaml.getList(path + ".contents", Collections.emptyList());
        List<ItemStack> armor = (List<ItemStack>) (List<?>)
            yaml.getList(path + ".armor", Collections.emptyList());
        Location location = new Location(world, yaml.getDouble(path + ".x"), yaml.getDouble(path + ".y"),
            yaml.getDouble(path + ".z"), (float) yaml.getDouble(path + ".yaw"),
            (float) yaml.getDouble(path + ".pitch"));
        return new StaffSnapshot(yaml.getString(path + ".game-mode", "SURVIVAL"), location,
            contents.toArray(new ItemStack[0]), armor.toArray(new ItemStack[0]), yaml.getInt(path + ".level"),
            (float) yaml.getDouble(path + ".experience"), yaml.getBoolean(path + ".vanished-before"));
    }
}