package me.jaime.emsichill.region;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import me.jaime.emsichill.Main;
import me.jaime.emsichill.config.ConfigFile;
import me.jaime.emsichill.util.CommandSuggestions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Implementa comandos, menús y reglas de protección de regiones. El índice espacial y los datos
 * persistentes pertenecen a RegionRepository.
 */
public final class RegionManager implements CommandExecutor, TabCompleter, Listener {
    private final Main plugin;
    private final RegionRepository repository;
    private ConfigFile configFile;

    public RegionManager(final Main plugin) {
        this.plugin = plugin;
        this.reloadConfiguration();
        this.repository = new RegionRepository(plugin);
    }

    public void reloadConfiguration() {
        if (this.configFile == null) this.configFile = new ConfigFile(this.plugin, "Regions/config.yml");
        else this.configFile.reload();
        this.migrateClaimLimit();
        this.migrateTierConfiguration();
    }

    private void migrateClaimLimit() {
        if (this.configFile.yaml().getInt("_meta.limit-revision", 0) >= 1) return;
        if (this.configFile.yaml().getInt("claims.default-limit", 1) == 1) {
            this.configFile.yaml().set("claims.default-limit", 2);
        }
        this.configFile.yaml().set("_meta.limit-revision", 1);
        this.configFile.save();
    }

    private void migrateTierConfiguration() {
        List<Map<?, ?>> configured = this.configFile.yaml().getMapList("upgrades");
        List<Map<String, Object>> migrated = new ArrayList<>();
        boolean changed = false;
        for (Map<?, ?> values : configured) {
            Map<String, Object> tier = new LinkedHashMap<>();
            Object radius = values.containsKey("radius") ? values.get("radius") : values.get("size");
            if (!values.containsKey("radius") && values.containsKey("size")) changed = true;
            tier.put("radius", radius);
            tier.put("cost-diamonds", values.containsKey("cost-diamonds") ? values.get("cost-diamonds") : 0);
            migrated.add(tier);
        }
        if (changed) {
            this.configFile.yaml().set("upgrades", migrated);
            this.configFile.save();
            this.plugin.getLogger().info("Regions/config.yml actualizado: los tamaños ahora se expresan como radio desde el centro.");
        }
    }

    public void stop() {
        this.repository.save();
    }

    public int regionCount() { return this.repository.regionCount(); }
    public int indexedChunkCount() { return this.repository.indexedChunkCount(); }
    public void persistData() { this.repository.save(); }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (!this.plugin.moduleEnabled("regions")) {
            this.plugin.messages().send(sender, "general.module-disabled");
            return true;
        }
        if (!(sender instanceof Player player)) {
            this.plugin.messages().send(sender, "general.only-players");
            return true;
        }
        if (args.length == 0) {
            this.sendHelp(player);
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "claim" -> this.claim(player, args);
            case "delete" -> this.delete(player, args);
            case "add" -> this.addMember(player, args, false);
            case "remove" -> this.removeMember(player, args, false);
            case "owner" -> this.addMember(player, args, true);
            case "unowner" -> this.removeMember(player, args, true);
            case "transfer" -> this.transfer(player, args);
            case "info" -> this.info(player, args);
            case "list" -> this.list(player);
            case "build" -> this.openBuild(player);
            case "teleport" -> this.teleport(player, args);
            case "view" -> this.view(player, args);
            case "upgrade" -> this.openUpgrade(player, args);
            case "settings" -> this.openSettings(player, args);
            case "help" -> {
                this.sendHelp(player);
                yield true;
            }
            default -> {
                this.sendHelp(player);
                yield true;
            }
        };
    }

    private void sendHelp(final Player player) {
        this.plugin.messages().send(player, "region.help-title");
        this.plugin.messages().send(player, "region.help-claim");
        this.plugin.messages().send(player, "region.help-build");
        this.plugin.messages().send(player, "region.help-teleport");
        this.plugin.messages().send(player, "region.help-view");
        this.plugin.messages().send(player, "region.help-people");
        this.plugin.messages().send(player, "region.help-settings");
        this.plugin.messages().send(player, "region.help-delete");
    }

    // El bloque actual es el centro; el radio se extiende igual en las cuatro direcciones.
    private boolean claim(final Player player, final String[] args) {
        if (!player.hasPermission("emsichill.region.claim")) {
            this.plugin.messages().send(player, "general.no-permission");
            return true;
        }
        if (args.length > 2) {
            this.plugin.messages().send(player, "region.claim-usage");
            return true;
        }
        int owned = this.ownedRegions(player.getUniqueId()).size();
        int limit = this.claimLimit(player);
        if (owned >= limit) {
            this.plugin.messages().send(player, "region.claim-limit", "{limit}", Integer.toString(limit));
            return true;
        }
        int radius = this.firstTier().radius();
        int centerX = player.getLocation().getBlockX();
        int centerZ = player.getLocation().getBlockZ();
        String regionName = args.length == 2 ? this.cleanName(args[1]) : this.nextAutomaticName(player.getUniqueId());
        if (regionName.isEmpty()) {
            this.plugin.messages().send(player, "region.invalid-name");
            return true;
        }
        if (this.hasOwnedName(player.getUniqueId(), regionName)) {
            this.plugin.messages().send(player, "region.name-exists", "{name}", regionName);
            return true;
        }
        Region candidate = new Region(UUID.randomUUID().toString(),
            regionName, player.getUniqueId(), player.getName(),
            player.getWorld().getName(), centerX - radius, centerX + radius, centerZ - radius, centerZ + radius,
            radius, player.getLocation().getBlockY());
        if (!this.insideWorldBorder(candidate)) {
            this.plugin.messages().send(player, "region.outside-border");
            return true;
        }
        Region overlapping = this.repository.overlapping(candidate, null);
        if (overlapping != null) {
            if (overlapping.primaryOwner().equals(player.getUniqueId())) {
                this.plugin.messages().send(player, "region.already-claimed", "{name}", overlapping.name());
            } else {
                this.plugin.messages().send(player, "region.too-close", "{name}", overlapping.name(),
                    "{owner}", overlapping.ownerName());
            }
            return true;
        }
        int minimumDistance = Math.max(0, this.configFile.yaml().getInt("claims.minimum-distance-blocks", 16));
        Region nearby = this.repository.nearbyForeign(candidate, player.getUniqueId(), minimumDistance);
        if (nearby != null) {
            this.plugin.messages().send(player, "region.too-close", "{name}", nearby.name(),
                "{owner}", nearby.ownerName());
            return true;
        }
        this.repository.add(candidate);
        this.repository.save();
        this.plugin.messages().send(player, "region.claimed", "{name}", candidate.name(),
            "{radius}", Integer.toString(radius), "{diameter}", Integer.toString(candidate.diameter()),
            "{dimension}", this.dimensionName(candidate), "{x}", Integer.toString(candidate.centerX()),
            "{z}", Integer.toString(candidate.centerZ()));
        this.showBoundary(player, candidate);
        return true;
    }

    private boolean delete(final Player player, final String[] args) {
        Region region = args.length == 2 ? this.ownedByName(player, args[1]) : this.manageableRegion(player);
        if (region == null || !this.canManage(player, region)) {
            this.plugin.messages().send(player, "region.not-found");
            return true;
        }
        this.repository.remove(region);
        this.repository.save();
        this.plugin.messages().send(player, "region.deleted", "{name}", region.name());
        return true;
    }

    private boolean addMember(final Player player, final String[] args, final boolean owner) {
        if (args.length != 2) {
            this.plugin.messages().send(player, owner ? "region.owner-usage" : "region.add-usage");
            return true;
        }
        Region region = this.manageableRegion(player);
        if (region == null || !this.canManage(player, region)) {
            this.plugin.messages().send(player, "region.not-found");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (target.getUniqueId().equals(region.primaryOwner())) {
            this.plugin.messages().send(player, "region.already-owner");
            return true;
        }
        if (owner) {
            region.coOwners().add(target.getUniqueId());
            region.members().remove(target.getUniqueId());
        } else if (!region.coOwners().contains(target.getUniqueId())) region.members().add(target.getUniqueId());
        this.repository.save();
        this.plugin.messages().send(player, owner ? "region.owner-added" : "region.member-added", "{player}", args[1]);
        return true;
    }

    private boolean removeMember(final Player player, final String[] args, final boolean owner) {
        if (args.length != 2) {
            this.plugin.messages().send(player, owner ? "region.unowner-usage" : "region.remove-usage");
            return true;
        }
        Region region = this.manageableRegion(player);
        if (region == null || !this.canManage(player, region)) {
            this.plugin.messages().send(player, "region.not-found");
            return true;
        }
        UUID target = Bukkit.getOfflinePlayer(args[1]).getUniqueId();
        boolean removed = owner ? region.coOwners().remove(target) : region.members().remove(target);
        if (removed) this.repository.save();
        this.plugin.messages().send(player, removed ? (owner ? "region.owner-removed" : "region.member-removed") : "region.player-not-added",
            "{player}", args[1]);
        return true;
    }

    private boolean transfer(final Player player, final String[] args) {
        Region region = this.manageableRegion(player);
        if (args.length != 2 || region == null || (!region.primaryOwner().equals(player.getUniqueId())
            && !player.hasPermission("emsichill.region.admin"))) {
            this.plugin.messages().send(player, "region.transfer-usage");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        UUID oldOwner = region.primaryOwner();
        region.setPrimaryOwner(target.getUniqueId(), args[1]);
        region.coOwners().remove(target.getUniqueId());
        region.coOwners().add(oldOwner);
        this.repository.save();
        this.plugin.messages().send(player, "region.transferred", "{player}", args[1]);
        return true;
    }

    private boolean info(final Player player, final String[] args) {
        Region region = this.selectedRegion(player, args);
        if (region == null) {
            this.plugin.messages().send(player, "region.not-found");
            return true;
        }
        this.plugin.messages().send(player, "region.info-title", "{name}", region.name());
        this.plugin.messages().send(player, "region.info-owner", "{owner}", region.ownerName(),
            "{owners}", Integer.toString(region.coOwners().size()), "{members}", Integer.toString(region.members().size()));
        this.plugin.messages().send(player, "region.info-area", "{radius}", Integer.toString(region.radius()),
            "{diameter}", Integer.toString(region.diameter()), "{x}", Integer.toString(region.centerX()),
            "{z}", Integer.toString(region.centerZ()), "{world}", this.dimensionName(region));
        this.plugin.messages().send(player, "region.info-settings", "{pvp}", this.status(region.pvp()),
            "{containers}", this.status(region.publicContainers()), "{interactions}", this.status(region.publicInteractions()));
        return true;
    }

    private boolean list(final Player player) {
        List<Region> owned = this.ownedRegions(player.getUniqueId());
        if (owned.isEmpty()) {
            this.plugin.messages().send(player, "region.none");
            return true;
        }
        owned.sort(java.util.Comparator.comparing(Region::name, String.CASE_INSENSITIVE_ORDER));
        this.plugin.messages().send(player, "region.list-header", "{count}", Integer.toString(owned.size()));
        for (Region region : owned) {
            this.plugin.messages().send(player, "region.list-entry", "{name}", region.name(),
                "{dimension}", this.dimensionName(region), "{x}", Integer.toString(region.centerX()),
                "{z}", Integer.toString(region.centerZ()), "{radius}", Integer.toString(region.radius()),
                "{diameter}", Integer.toString(region.diameter()));
        }
        return true;
    }

    // Comprar añade un cupo persistente; el terreno se reclama después con /region claim.
    private boolean openBuild(final Player player) {
        if (!this.configFile.yaml().getBoolean("purchases.enabled", true)) {
            this.plugin.messages().send(player, "region.build-disabled");
            return true;
        }
        int purchased = this.repository.purchasedSlots(player.getUniqueId());
        int maximum = Math.max(1, this.configFile.yaml().getInt("purchases.maximum-extra-slots", 6));
        if (purchased >= maximum) {
            this.plugin.messages().send(player, "region.build-maximum", "{maximum}", Integer.toString(maximum));
            return true;
        }
        int cost = this.buildCost(purchased);
        BuildHolder holder = new BuildHolder(player.getUniqueId(), purchased, cost);
        Inventory inventory = Bukkit.createInventory(holder, 27, Component.text("Comprar cupo de región"));
        holder.setInventory(inventory);
        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Comprar cupo adicional", NamedTextColor.GREEN));
        meta.lore(List.of(
            Component.text("Costo: " + cost + " esmeraldas", NamedTextColor.WHITE),
            Component.text("Límite actual: " + this.claimLimitWithoutUnlimited(player), NamedTextColor.GRAY),
            Component.text("Nuevo límite: " + (this.claimLimitWithoutUnlimited(player) + 1), NamedTextColor.AQUA),
            Component.empty(),
            Component.text("Haz clic para confirmar la compra.", NamedTextColor.YELLOW)));
        item.setItemMeta(meta);
        inventory.setItem(13, item);
        player.openInventory(inventory);
        return true;
    }

    private int buildCost(final int purchased) {
        long cost = Math.max(1, this.configFile.yaml().getLong("purchases.base-cost-emeralds", 64));
        long multiplier = Math.max(1, this.configFile.yaml().getLong("purchases.cost-multiplier", 2));
        for (int index = 0; index < purchased; index++) {
            if (cost > Integer.MAX_VALUE / multiplier) return Integer.MAX_VALUE;
            cost *= multiplier;
        }
        return (int) cost;
    }

    private int countMaterial(final Player player, final Material material) {
        int total = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.getType() == material) total += item.getAmount();
        }
        return total;
    }

    private void removeMaterial(final Player player, final Material material, final int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType() != material) continue;
            int removed = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - removed);
            remaining -= removed;
            contents[slot] = item.getAmount() == 0 ? null : item;
        }
        player.getInventory().setStorageContents(contents);
    }

    private boolean teleport(final Player player, final String[] args) {
        if (args.length != 2) {
            this.plugin.messages().send(player, "region.teleport-usage");
            return true;
        }
        Region region = this.ownedRegionByName(player.getUniqueId(), args[1]);
        if (region == null) {
            this.plugin.messages().send(player, "region.teleport-not-owned", "{name}", args[1]);
            return true;
        }
        World world = Bukkit.getWorld(region.world());
        if (world == null) {
            this.plugin.messages().send(player, "region.teleport-world-missing");
            return true;
        }
        Location destination = this.safeTeleportDestination(world, region);
        if (destination == null) {
            this.plugin.messages().send(player, "region.teleport-unsafe", "{name}", region.name());
            return true;
        }
        destination.setYaw(player.getLocation().getYaw());
        destination.setPitch(player.getLocation().getPitch());
        if (!player.teleport(destination)) {
            this.plugin.messages().send(player, "region.teleport-failed");
            return true;
        }
        this.plugin.messages().send(player, "region.teleported", "{name}", region.name(),
            "{dimension}", this.dimensionName(region), "{x}", Integer.toString(destination.getBlockX()),
            "{y}", Integer.toString(destination.getBlockY()), "{z}", Integer.toString(destination.getBlockZ()));
        return true;
    }

    // Busca desde el centro hacia afuera y prioriza una altura cercana al punto de reclamo.
    private Location safeTeleportDestination(final World world, final Region region) {
        for (int distance = 0; distance <= 6; distance++) {
            for (int offsetX = -distance; offsetX <= distance; offsetX++) {
                for (int offsetZ = -distance; offsetZ <= distance; offsetZ++) {
                    if (distance > 0 && Math.abs(offsetX) != distance && Math.abs(offsetZ) != distance) continue;
                    int x = region.centerX() + offsetX;
                    int z = region.centerZ() + offsetZ;
                    world.getChunkAt(x >> 4, z >> 4).load();
                    Integer y = this.nearestSafeY(world, x, z, region.centerY());
                    if (y != null) return new Location(world, x + 0.5, y, z + 0.5);
                }
            }
        }
        return null;
    }

    private Integer nearestSafeY(final World world, final int x, final int z, final int preferredY) {
        int minimum = world.getMinHeight() + 1;
        int maximum = world.getMaxHeight() - 2;
        int start = Math.max(minimum, Math.min(maximum, preferredY));
        int range = maximum - minimum;
        for (int distance = 0; distance <= range; distance++) {
            int above = start + distance;
            if (above <= maximum && this.isSafeTeleportBlock(world, x, above, z)) return above;
            int below = start - distance;
            if (distance > 0 && below >= minimum && this.isSafeTeleportBlock(world, x, below, z)) return below;
        }
        return null;
    }

    private boolean isSafeTeleportBlock(final World world, final int x, final int y, final int z) {
        Block floor = world.getBlockAt(x, y - 1, z);
        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        return floor.getType().isSolid() && feet.isPassable() && head.isPassable()
            && !feet.isLiquid() && !head.isLiquid() && !this.dangerousTeleportMaterial(floor.getType())
            && !this.dangerousTeleportMaterial(feet.getType()) && !this.dangerousTeleportMaterial(head.getType());
    }

    private boolean dangerousTeleportMaterial(final Material material) {
        return switch (material) {
            case LAVA, FIRE, SOUL_FIRE, MAGMA_BLOCK, CACTUS, CAMPFIRE, SOUL_CAMPFIRE, POWDER_SNOW,
                SWEET_BERRY_BUSH, WITHER_ROSE -> true;
            default -> false;
        };
    }

    private boolean view(final Player player, final String[] args) {
        Region region = this.selectedRegion(player, args);
        if (region == null) {
            this.plugin.messages().send(player, "region.not-found");
            return true;
        }
        this.showBoundary(player, region);
        return true;
    }

    // Las líneas celestes marcan límites y la columna amarilla identifica el centro.
    private void showBoundary(final Player player, final Region region) {
        World world = Bukkit.getWorld(region.world());
        if (world == null || !player.getWorld().equals(world)) {
            this.plugin.messages().send(player, "region.wrong-world");
            return;
        }
        int duration = Math.max(2, this.configFile.yaml().getInt("view.duration-seconds", 10));
        int borderSpacing = Math.max(1, Math.min(8, this.configFile.yaml().getInt("view.border-spacing-blocks", 2)));
        int pillarSpacing = Math.max(2, Math.min(16, this.configFile.yaml().getInt("view.pillar-spacing-blocks", 8)));
        int pillarHeight = Math.max(2, Math.min(12, this.configFile.yaml().getInt("view.pillar-height-blocks", 6)));
        Particle.DustOptions boundaryDust = new Particle.DustOptions(Color.AQUA, 1.5F);
        Particle.DustOptions centerDust = new Particle.DustOptions(Color.YELLOW, 1.5F);
        final int[] runs = {0};
        Bukkit.getScheduler().runTaskTimer(this.plugin, task -> {
            if (!player.isOnline() || runs[0]++ >= duration * 2) {
                task.cancel();
                return;
            }
            double y = Math.max(world.getMinHeight() + 1, Math.min(world.getMaxHeight() - 2, player.getLocation().getY() + 0.2));
            double upperY = Math.min(world.getMaxHeight() - 1, y + 3.0);
            for (int x = region.minX(); x <= region.maxX() + 1; x += borderSpacing) {
                player.spawnParticle(Particle.DUST, x, y, region.minZ(), 1, boundaryDust);
                player.spawnParticle(Particle.DUST, x, y, region.maxZ() + 1, 1, boundaryDust);
                player.spawnParticle(Particle.DUST, x, upperY, region.minZ(), 1, boundaryDust);
                player.spawnParticle(Particle.DUST, x, upperY, region.maxZ() + 1, 1, boundaryDust);
            }
            for (int z = region.minZ(); z <= region.maxZ() + 1; z += borderSpacing) {
                player.spawnParticle(Particle.DUST, region.minX(), y, z, 1, boundaryDust);
                player.spawnParticle(Particle.DUST, region.maxX() + 1, y, z, 1, boundaryDust);
                player.spawnParticle(Particle.DUST, region.minX(), upperY, z, 1, boundaryDust);
                player.spawnParticle(Particle.DUST, region.maxX() + 1, upperY, z, 1, boundaryDust);
            }
            for (int x = region.minX(); x <= region.maxX() + 1; x += pillarSpacing) {
                for (int vertical = 0; vertical <= pillarHeight; vertical++) {
                    double py = Math.min(world.getMaxHeight() - 1, y + vertical);
                    player.spawnParticle(Particle.DUST, x, py, region.minZ(), 1, boundaryDust);
                    player.spawnParticle(Particle.DUST, x, py, region.maxZ() + 1, 1, boundaryDust);
                }
            }
            for (int z = region.minZ(); z <= region.maxZ() + 1; z += pillarSpacing) {
                for (int vertical = 0; vertical <= pillarHeight; vertical++) {
                    double py = Math.min(world.getMaxHeight() - 1, y + vertical);
                    player.spawnParticle(Particle.DUST, region.minX(), py, z, 1, boundaryDust);
                    player.spawnParticle(Particle.DUST, region.maxX() + 1, py, z, 1, boundaryDust);
                }
            }
            for (int vertical = 0; vertical <= pillarHeight; vertical++) {
                double py = Math.min(world.getMaxHeight() - 1, y + vertical);
                player.spawnParticle(Particle.DUST, region.centerX() + 0.5, py, region.centerZ() + 0.5, 2, centerDust);
            }
            for (double offset = -2.0; offset <= 2.0; offset += 0.5) {
                player.spawnParticle(Particle.DUST, region.centerX() + 0.5 + offset, y, region.centerZ() + 0.5, 1, centerDust);
                player.spawnParticle(Particle.DUST, region.centerX() + 0.5, y, region.centerZ() + 0.5 + offset, 1, centerDust);
            }
        }, 0L, 10L);
        this.plugin.messages().send(player, "region.viewing", "{seconds}", Integer.toString(duration),
            "{radius}", Integer.toString(region.radius()), "{diameter}", Integer.toString(region.diameter()));
    }

    private boolean openUpgrade(final Player player, final String[] args) {
        Region region = this.selectedRegion(player, args);
        if (region == null || !this.canManage(player, region)) {
            this.plugin.messages().send(player, "region.not-found");
            return true;
        }
        Tier next = this.nextTier(region.radius());
        if (next == null) {
            this.plugin.messages().send(player, "region.max-size");
            return true;
        }
        UpgradeHolder holder = new UpgradeHolder(region.id(), next.radius(), next.cost());
        Inventory inventory = Bukkit.createInventory(holder, 27, Component.text("Ampliar " + region.name()));
        holder.setInventory(inventory);
        ItemStack item = new ItemStack(Material.DIAMOND_BLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Radio " + next.radius() + " (" + diameter(next.radius()) + " x "
            + diameter(next.radius()) + ")", NamedTextColor.AQUA));
        meta.lore(List.of(
            Component.text("Radio actual: " + region.radius() + " bloques", NamedTextColor.GRAY),
            Component.text("Área actual: " + region.diameter() + " x " + region.diameter(), NamedTextColor.GRAY),
            Component.text("Nuevo radio: " + next.radius() + " bloques", NamedTextColor.WHITE),
            Component.text("Área nueva: " + diameter(next.radius()) + " x " + diameter(next.radius()), NamedTextColor.WHITE),
            Component.text("Protege desde la altura mínima hasta la máxima.", NamedTextColor.GRAY),
            Component.empty(),
            Component.text("Costo: " + next.cost() + " diamantes", NamedTextColor.YELLOW),
            Component.text("Haz clic para confirmar la mejora.", NamedTextColor.GREEN)));
        item.setItemMeta(meta);
        inventory.setItem(13, item);
        player.openInventory(inventory);
        return true;
    }

    private boolean openSettings(final Player player, final String[] args) {
        Region region = this.selectedRegion(player, args);
        if (region == null || !this.canManage(player, region)) {
            this.plugin.messages().send(player, "region.not-found");
            return true;
        }
        SettingsHolder holder = new SettingsHolder(region.id());
        Inventory inventory = Bukkit.createInventory(holder, 27, Component.text("Ajustes de " + region.name()));
        holder.setInventory(inventory);
        inventory.setItem(11, this.settingItem(Material.IRON_SWORD, "Combate entre jugadores", region.pvp(),
            "Permite que los jugadores se hagan daño", "dentro de esta región."));
        inventory.setItem(13, this.settingItem(Material.CHEST, "Contenedores públicos", region.publicContainers(),
            "Permite que visitantes abran cofres,", "barriles y otros contenedores."));
        inventory.setItem(15, this.settingItem(Material.LEVER, "Interacciones públicas", region.publicInteractions(),
            "Permite que visitantes usen puertas,", "botones, palancas y entidades."));
        player.openInventory(inventory);
        return true;
    }

    private ItemStack settingItem(final Material material, final String name, final boolean enabled, final String... description) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name + ": " + (enabled ? "ACTIVADO" : "DESACTIVADO"),
            enabled ? NamedTextColor.GREEN : NamedTextColor.RED));
        List<Component> lore = new ArrayList<>();
        for (String line : description) lore.add(Component.text(line, NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text(enabled ? "Haz clic para bloquearlo." : "Haz clic para permitirlo.", NamedTextColor.YELLOW));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onUpgradeClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        // La compra se revalida al hacer clic para evitar precios antiguos o dobles cobros.
        if (holder instanceof BuildHolder build) {
            event.setCancelled(true);
            if (event.getRawSlot() != 13 || !build.owner().equals(player.getUniqueId())) return;
            int purchased = this.repository.purchasedSlots(player.getUniqueId());
            if (purchased != build.purchaseIndex() || this.buildCost(purchased) != build.cost()) {
                player.closeInventory();
                this.plugin.messages().send(player, "region.build-changed");
                return;
            }
            if (this.countMaterial(player, Material.EMERALD) < build.cost()) {
                this.plugin.messages().send(player, "region.build-not-enough", "{cost}", Integer.toString(build.cost()));
                return;
            }
            this.removeMaterial(player, Material.EMERALD, build.cost());
            this.repository.addPurchasedSlot(player.getUniqueId());
            this.repository.save();
            player.closeInventory();
            this.plugin.messages().send(player, "region.build-purchased", "{cost}", Integer.toString(build.cost()),
                "{limit}", Integer.toString(this.claimLimitWithoutUnlimited(player)));
        } else if (holder instanceof UpgradeHolder upgrade) {
            event.setCancelled(true);
            if (event.getRawSlot() != 13) return;
            Region region = this.repository.get(upgrade.regionId());
            if (region == null || !this.canManage(player, region)) return;
            Region expanded = region.expanded(upgrade.radius());
            if (!this.insideWorldBorder(expanded) || this.repository.overlapping(expanded, region.id()) != null) {
                player.closeInventory();
                this.plugin.messages().send(player, "region.upgrade-blocked");
                return;
            }
            ItemStack payment = new ItemStack(Material.DIAMOND, upgrade.cost());
            if (!player.getInventory().containsAtLeast(payment, upgrade.cost())) {
                this.plugin.messages().send(player, "region.not-enough-diamonds", "{cost}", Integer.toString(upgrade.cost()));
                return;
            }
            player.getInventory().removeItem(payment);
            this.repository.reindex(region, () -> region.resize(expanded));
            this.repository.save();
            player.closeInventory();
            this.plugin.messages().send(player, "region.upgraded", "{radius}", Integer.toString(region.radius()),
                "{diameter}", Integer.toString(region.diameter()), "{cost}", Integer.toString(upgrade.cost()));
            this.showBoundary(player, region);
        } else if (holder instanceof SettingsHolder settings) {
            event.setCancelled(true);
            Region region = this.repository.get(settings.regionId());
            if (region == null || !this.canManage(player, region)) return;
            if (event.getRawSlot() == 11) region.setPvp(!region.pvp());
            else if (event.getRawSlot() == 13) region.setPublicContainers(!region.publicContainers());
            else if (event.getRawSlot() == 15) region.setPublicInteractions(!region.publicInteractions());
            else return;
            this.repository.save();
            this.openSettings(player, new String[] {"settings", region.name()});
        }
    }

    private boolean canBuild(final Player player, final Region region) {
        return player.hasPermission("emsichill.region.admin") || region.primaryOwner().equals(player.getUniqueId())
            || region.coOwners().contains(player.getUniqueId()) || region.members().contains(player.getUniqueId());
    }

    private boolean canManage(final Player player, final Region region) {
        return player.hasPermission("emsichill.region.admin") || region.primaryOwner().equals(player.getUniqueId())
            || region.coOwners().contains(player.getUniqueId());
    }

    private void deny(final Player player) {
        this.plugin.messages().send(player, "region.protected");
    }

    // Todos los cambios de bloques pasan por la misma regla de propietario y miembros.
    @EventHandler(ignoreCancelled = true)
    public void onBreak(final BlockBreakEvent event) {
        Region region = this.repository.at(event.getBlock().getLocation());
        if (region != null && !this.canBuild(event.getPlayer(), region)) { event.setCancelled(true); this.deny(event.getPlayer()); }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(final BlockPlaceEvent event) {
        Region region = this.repository.at(event.getBlock().getLocation());
        if (region != null && !this.canBuild(event.getPlayer(), region)) { event.setCancelled(true); this.deny(event.getPlayer()); }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(final PlayerBucketEmptyEvent event) {
        Region region = this.repository.at(event.getBlockClicked().getRelative(event.getBlockFace()).getLocation());
        if (region != null && !this.canBuild(event.getPlayer(), region)) { event.setCancelled(true); this.deny(event.getPlayer()); }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(final PlayerBucketFillEvent event) {
        Region region = this.repository.at(event.getBlockClicked().getLocation());
        if (region != null && !this.canBuild(event.getPlayer(), region)) { event.setCancelled(true); this.deny(event.getPlayer()); }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(final PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) return;
        Region region = this.repository.at(block.getLocation());
        if (region == null || this.canBuild(event.getPlayer(), region)) return;
        boolean container = block.getState() instanceof InventoryHolder;
        String material = block.getType().name();
        boolean interaction = material.contains("DOOR") || material.contains("BUTTON") || material.contains("GATE")
            || block.getType() == Material.LEVER || material.contains("PRESSURE_PLATE");
        if ((container && !region.publicContainers()) || (interaction && !region.publicInteractions())) {
            event.setCancelled(true);
            this.deny(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityInteract(final PlayerInteractEntityEvent event) {
        Region region = this.repository.at(event.getRightClicked().getLocation());
        if (region != null && !this.canBuild(event.getPlayer(), region) && !region.publicInteractions()) {
            event.setCancelled(true); this.deny(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingBreak(final HangingBreakByEntityEvent event) {
        if (!(event.getRemover() instanceof Player player)) return;
        Region region = this.repository.at(event.getEntity().getLocation());
        if (region != null && !this.canBuild(player, region)) { event.setCancelled(true); this.deny(player); }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingPlace(final HangingPlaceEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        Region region = this.repository.at(event.getEntity().getLocation());
        if (region != null && !this.canBuild(player, region)) { event.setCancelled(true); this.deny(player); }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPvp(final EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = event.getDamager() instanceof Player value ? value : null;
        if (attacker == null && event.getDamager() instanceof org.bukkit.entity.Projectile projectile
            && projectile.getShooter() instanceof Player value) attacker = value;
        if (attacker == null) return;
        Region region = this.repository.at(victim.getLocation());
        if (region != null && !region.pvp()) event.setCancelled(true);
    }

    private Region manageableRegion(final Player player) {
        Region current = this.repository.at(player.getLocation());
        if (current != null && this.canManage(player, current)) return current;
        List<Region> owned = this.ownedRegions(player.getUniqueId());
        return owned.size() == 1 ? owned.get(0) : null;
    }

    private Region selectedRegion(final Player player, final String[] args) {
        if (args.length >= 2) return this.ownedByName(player, args[1]);
        return this.manageableRegion(player);
    }

    private String status(final boolean enabled) {
        return enabled ? "Activado" : "Desactivado";
    }

    private String dimensionName(final Region region) {
        World world = Bukkit.getWorld(region.world());
        if (world == null) return region.world();
        return switch (world.getEnvironment()) {
            case NORMAL -> "Mundo normal";
            case NETHER -> "Nether";
            case THE_END -> "End";
            default -> world.getName();
        };
    }

    private boolean hasOwnedName(final UUID owner, final String name) {
        for (Region region : this.repository.all()) {
            if (region.primaryOwner().equals(owner) && region.name().equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private String nextAutomaticName(final UUID owner) {
        int number = 1;
        while (this.hasOwnedName(owner, "region" + number)) number++;
        return "region" + number;
    }

    private static int diameter(final int radius) {
        return radius * 2 + 1;
    }

    private Region ownedByName(final Player player, final String name) {
        for (Region region : this.repository.all()) {
            if (region.name().equalsIgnoreCase(name) && this.canManage(player, region)) return region;
        }
        return null;
    }

    private Region ownedRegionByName(final UUID owner, final String name) {
        for (Region region : this.repository.all()) {
            if (region.primaryOwner().equals(owner) && region.name().equalsIgnoreCase(name)) return region;
        }
        return null;
    }

    private List<Region> ownedRegions(final UUID owner) {
        List<Region> result = new ArrayList<>();
        for (Region region : this.repository.all()) if (region.primaryOwner().equals(owner)) result.add(region);
        return result;
    }

    private boolean insideWorldBorder(final Region region) {
        World world = Bukkit.getWorld(region.world());
        if (world == null) return false;
        return world.getWorldBorder().isInside(new Location(world, region.minX(), 64, region.minZ()))
            && world.getWorldBorder().isInside(new Location(world, region.maxX(), 64, region.maxZ()));
    }

    private int claimLimit(final Player player) {
        if (player.hasPermission("emsichill.region.unlimited")) return Integer.MAX_VALUE;
        return this.claimLimitWithoutUnlimited(player);
    }

    private int claimLimitWithoutUnlimited(final Player player) {
        int base = Math.max(1, this.configFile.yaml().getInt("claims.default-limit", 2));
        return base + Math.max(0, this.repository.purchasedSlots(player.getUniqueId()));
    }

    private String cleanName(final String value) {
        String cleaned = value.toLowerCase(Locale.ROOT);
        return cleaned.matches("[a-z0-9_-]{1,24}") ? cleaned : "";
    }

    private Tier firstTier() {
        List<Tier> tiers = this.tiers();
        return tiers.isEmpty() ? new Tier(32, 0) : tiers.get(0);
    }

    private Tier nextTier(final int currentSize) {
        for (Tier tier : this.tiers()) if (tier.radius() > currentSize) return tier;
        return null;
    }

    private List<Tier> tiers() {
        List<Tier> result = new ArrayList<>();
        for (Map<?, ?> values : this.configFile.yaml().getMapList("upgrades")) {
            Object radius = values.containsKey("radius") ? values.get("radius") : values.get("size");
            Object cost = values.get("cost-diamonds");
            if (radius instanceof Number radiusNumber && cost instanceof Number costNumber)
                result.add(new Tier(Math.max(4, radiusNumber.intValue()), Math.max(0, costNumber.intValue())));
        }
        result.sort(java.util.Comparator.comparingInt(Tier::radius));
        return result;
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args) {
        if (args.length == 1) return CommandSuggestions.filter(List.of("claim", "delete", "add", "remove", "owner",
            "unowner", "transfer", "info", "list", "build", "teleport", "view", "upgrade", "settings", "help"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("claim")) {
            return args[1].isEmpty() ? Collections.singletonList("(Nombre)") : Collections.emptyList();
        }
        if (args.length == 2 && List.of("add", "remove", "owner", "unowner", "transfer").contains(args[0].toLowerCase(Locale.ROOT))) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) names.add(player.getName());
            return CommandSuggestions.filter(names, args[1]);
        }
        if (args.length == 2 && List.of("delete", "info", "view", "upgrade", "settings").contains(args[0].toLowerCase(Locale.ROOT))
            && sender instanceof Player player) {
            List<String> names = new ArrayList<>();
            for (Region region : this.repository.all()) if (this.canManage(player, region)) names.add(region.name());
            return CommandSuggestions.filter(names, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("teleport") && sender instanceof Player player) {
            List<String> names = new ArrayList<>();
            for (Region region : this.ownedRegions(player.getUniqueId())) names.add(region.name());
            return CommandSuggestions.filter(names, args[1]);
        }
        return Collections.emptyList();
    }

    private static final class UpgradeHolder implements InventoryHolder {
        private final String regionId; private final int radius; private final int cost; private Inventory inventory;
        private UpgradeHolder(final String regionId, final int radius, final int cost) { this.regionId = regionId; this.radius = radius; this.cost = cost; }
        private String regionId() { return this.regionId; } private int radius() { return this.radius; } private int cost() { return this.cost; }
        private void setInventory(final Inventory value) { this.inventory = value; }
        @Override public Inventory getInventory() { return this.inventory; }
    }

    private static final class BuildHolder implements InventoryHolder {
        private final UUID owner; private final int purchaseIndex; private final int cost; private Inventory inventory;
        private BuildHolder(final UUID owner, final int purchaseIndex, final int cost) {
            this.owner = owner; this.purchaseIndex = purchaseIndex; this.cost = cost;
        }
        private UUID owner() { return this.owner; } private int purchaseIndex() { return this.purchaseIndex; }
        private int cost() { return this.cost; } private void setInventory(final Inventory value) { this.inventory = value; }
        @Override public Inventory getInventory() { return this.inventory; }
    }

    private static final class SettingsHolder implements InventoryHolder {
        private final String regionId; private Inventory inventory;
        private SettingsHolder(final String regionId) { this.regionId = regionId; }
        private String regionId() { return this.regionId; } private void setInventory(final Inventory value) { this.inventory = value; }
        @Override public Inventory getInventory() { return this.inventory; }
    }
}