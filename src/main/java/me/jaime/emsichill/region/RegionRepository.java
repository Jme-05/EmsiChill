package me.jaime.emsichill.region;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import me.jaime.emsichill.Main;

/**
 * Fuente de verdad de regiones y cupos comprados. Mantiene un índice por chunks para evitar
 * recorrer todas las regiones en cada interacción del mundo.
 */
final class RegionRepository {
    private final Main plugin;
    private final File dataFile;
    private final Map<String, Region> regions = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> chunkIndex = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> purchasedSlots = new ConcurrentHashMap<>();

    RegionRepository(final Main plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "Regions/data.yml");
        this.load();
    }

    Collection<Region> all() {
        return this.regions.values();
    }

    Region get(final String id) {
        return this.regions.get(id);
    }

    void add(final Region region) {
        this.regions.put(region.id(), region);
        this.index(region);
    }

    void remove(final Region region) {
        this.deindex(region);
        this.regions.remove(region.id());
    }

    int regionCount() {
        return this.regions.size();
    }

    int indexedChunkCount() {
        return this.chunkIndex.size();
    }

    int purchasedSlots(final UUID owner) {
        return this.purchasedSlots.getOrDefault(owner, 0);
    }

    void addPurchasedSlot(final UUID owner) {
        this.purchasedSlots.merge(owner, 1, Integer::sum);
    }

    Region at(final Location location) {
        if (location.getWorld() == null) {
            return null;
        }
        Set<String> ids = this.chunkIndex.get(chunkKey(location.getWorld().getName(),
            location.getBlockX() >> 4, location.getBlockZ() >> 4));
        if (ids == null) {
            return null;
        }
        for (String id : ids) {
            Region region = this.regions.get(id);
            if (region != null && region.contains(location)) {
                return region;
            }
        }
        return null;
    }

    Region overlapping(final Region candidate, final String ignoredId) {
        for (Region region : this.indexedCandidates(candidate, 0)) {
            if ((ignoredId == null || !region.id().equals(ignoredId)) && candidate.overlaps(region)) {
                return region;
            }
        }
        return null;
    }

    Region nearbyForeign(final Region candidate, final UUID owner, final int distance) {
        if (distance <= 0) {
            return null;
        }
        for (Region region : this.indexedCandidates(candidate, distance)) {
            if (!region.primaryOwner().equals(owner) && candidate.isWithinDistance(region, distance)) {
                return region;
            }
        }
        return null;
    }

    void reindex(final Region region, final Runnable mutation) {
        // Los límites viejos deben salir del índice antes de redimensionar la región.
        this.deindex(region);
        mutation.run();
        this.index(region);
    }

    void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Region region : this.regions.values()) {
            region.save(yaml, "regions." + region.id());
        }
        for (Map.Entry<UUID, Integer> entry : this.purchasedSlots.entrySet()) {
            yaml.set("purchased-slots." + entry.getKey(), entry.getValue());
        }
        this.plugin.dataStore().saveAsync(this.dataFile, yaml);
    }

    private Set<Region> indexedCandidates(final Region region, final int expansion) {
        // Solo se visitan chunks que podrían cruzarse con el rectángulo consultado.
        Set<Region> result = new HashSet<>();
        int minChunkX = Math.floorDiv(region.minX() - expansion, 16);
        int maxChunkX = Math.floorDiv(region.maxX() + expansion, 16);
        int minChunkZ = Math.floorDiv(region.minZ() - expansion, 16);
        int maxChunkZ = Math.floorDiv(region.maxZ() + expansion, 16);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                Set<String> ids = this.chunkIndex.get(chunkKey(region.world(), chunkX, chunkZ));
                if (ids == null) {
                    continue;
                }
                for (String id : ids) {
                    Region candidate = this.regions.get(id);
                    if (candidate != null) {
                        result.add(candidate);
                    }
                }
            }
        }
        return result;
    }

    private void index(final Region region) {
        for (int chunkX = Math.floorDiv(region.minX(), 16); chunkX <= Math.floorDiv(region.maxX(), 16); chunkX++) {
            for (int chunkZ = Math.floorDiv(region.minZ(), 16); chunkZ <= Math.floorDiv(region.maxZ(), 16); chunkZ++) {
                this.chunkIndex.computeIfAbsent(chunkKey(region.world(), chunkX, chunkZ),
                    ignored -> ConcurrentHashMap.newKeySet()).add(region.id());
            }
        }
    }

    private void deindex(final Region region) {
        for (int chunkX = Math.floorDiv(region.minX(), 16); chunkX <= Math.floorDiv(region.maxX(), 16); chunkX++) {
            for (int chunkZ = Math.floorDiv(region.minZ(), 16); chunkZ <= Math.floorDiv(region.maxZ(), 16); chunkZ++) {
                String key = chunkKey(region.world(), chunkX, chunkZ);
                Set<String> ids = this.chunkIndex.get(key);
                if (ids == null) {
                    continue;
                }
                ids.remove(region.id());
                if (ids.isEmpty()) {
                    this.chunkIndex.remove(key, ids);
                }
            }
        }
    }

    private void load() {
        if (!this.dataFile.exists()) {
            return;
        }
        YamlConfiguration yaml = this.plugin.dataStore().load(this.dataFile);
        ConfigurationSection regionSection = yaml.getConfigurationSection("regions");
        if (regionSection != null) {
            for (String id : regionSection.getKeys(false)) {
                Region region = Region.load(yaml, "regions." + id, id);
                if (region != null) {
                    this.add(region);
                }
            }
        }
        ConfigurationSection slots = yaml.getConfigurationSection("purchased-slots");
        if (slots != null) {
            for (String value : slots.getKeys(false)) {
                try {
                    int purchased = Math.max(0, slots.getInt(value));
                    if (purchased > 0) {
                        this.purchasedSlots.put(UUID.fromString(value), purchased);
                    }
                } catch (IllegalArgumentException exception) {
                    this.plugin.getLogger().warning("Se ignoró un registro de cupos de región inválido: " + value);
                }
            }
        }
        this.save();
    }

    private static String chunkKey(final String world, final int chunkX, final int chunkZ) {
        return world + ':' + chunkX + ':' + chunkZ;
    }
}