package me.jaime.emsichill.grave;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import me.jaime.emsichill.Main;
import me.jaime.emsichill.config.ConfigFile;
import me.jaime.emsichill.util.CommandSuggestions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Controls deaths, grave storage, item recovery, visible markers and marker protection. */
public final class GraveManager implements CommandExecutor, TabCompleter, Listener {
    private final Main plugin;
    private final GraveRepository repository;
    private final Map<String, String> graveBlocks = new ConcurrentHashMap<>();
    private final Map<String, List<Display>> graveDisplays = new ConcurrentHashMap<>();
    private final Set<String> openGraves = ConcurrentHashMap.newKeySet();
    private ConfigFile configFile;
    private BukkitTask cleanupTask;

    public GraveManager(final Main plugin) {
        this.plugin = plugin;
        this.reloadConfiguration();
        this.repository = new GraveRepository(plugin);
    }

    public void reloadConfiguration() {
        if (this.configFile == null) this.configFile = new ConfigFile(this.plugin, "Graves/config.yml");
        else this.configFile.reload();
        this.migrateVisualDefaults();
        if (this.repository != null && this.enabled()) this.restoreMarkers();
    }

    private void migrateVisualDefaults() {
        boolean changed = false;
        String markerPath = "graves.visuals.marker.";
        if (this.configFile.yaml().getInt(markerPath + "style-version", 0) < 3) {
            this.configFile.yaml().set(markerPath + "style-version", 3);
            this.configFile.yaml().set(markerPath + "title-height", 1.58D);
            this.configFile.yaml().set(markerPath + "title-scale", 0.5D);
            this.configFile.yaml().set(markerPath + "title", "{player}");
            this.configFile.yaml().set(markerPath + "base", true);
            this.configFile.yaml().set(markerPath + "base-material", "POLISHED_DEEPSLATE");
            this.configFile.yaml().set(markerPath + "step", true);
            this.configFile.yaml().set(markerPath + "step-material", "DEEPSLATE_TILES");
            this.configFile.yaml().set(markerPath + "headstone", true);
            this.configFile.yaml().set(markerPath + "headstone-material", "DEEPSLATE_BRICKS");
            this.configFile.yaml().set(markerPath + "trim-material", "POLISHED_BLACKSTONE");
            this.configFile.yaml().set(markerPath + "plaque", true);
            this.configFile.yaml().set(markerPath + "plaque-material", "CHISELED_DEEPSLATE");
            this.configFile.yaml().set(markerPath + "soul-lantern", true);
            this.configFile.yaml().set(markerPath + "soul-lantern-material", "SOUL_LANTERN");
            this.configFile.yaml().set(markerPath + "subtitle", null);
            this.configFile.yaml().set(markerPath + "lid", null);
            this.configFile.yaml().set(markerPath + "lid-material", null);
            this.configFile.yaml().set(markerPath + "front-plaque", null);
            this.configFile.yaml().set(markerPath + "front-plaque-material", null);
            this.configFile.yaml().set(markerPath + "cap-material", null);
            this.configFile.yaml().set(markerPath + "relic", null);
            this.configFile.yaml().set(markerPath + "relic-material", null);
            this.configFile.yaml().set(markerPath + "side-chains", null);
            this.configFile.yaml().set(markerPath + "side-material", null);
            changed = true;
        }
        if (changed) this.configFile.save();
    }

    public void start() {
        if (!this.enabled()) return;
        if (this.cleanupTask != null) this.cleanupTask.cancel();
        this.cleanupTask = Bukkit.getScheduler().runTaskTimer(this.plugin, this::expireGraves, 1200L, 1200L);
        this.restoreMarkers();
    }

    public void stop() {
        if (this.cleanupTask != null) this.cleanupTask.cancel();
        this.clearGraveDisplays();
        this.repository.save();
    }

    public int activeGraveCount() { return this.repository.size(); }
    public void persistData() { this.repository.saveNow(); }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (!this.plugin.moduleEnabled("graves")) {
            this.plugin.messages().send(sender, "general.module-disabled");
            return true;
        }
        if (command.getName().equalsIgnoreCase("deathcontrol")) return this.deathControl(sender, args);
        if (!sender.hasPermission("emsichill.grave.admin")) {
            this.plugin.messages().send(sender, "general.no-permission");
            return true;
        }
        if (!(sender instanceof Player player)) {
            this.plugin.messages().send(sender, "general.only-players");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) return this.listGraves(player);
        if (args.length == 2 && args[0].equalsIgnoreCase("locate")) return this.locate(player, args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("recover")) return this.recoverById(player, args[1], player);
        if (args.length == 3 && args[0].equalsIgnoreCase("admin")
            && args[1].equalsIgnoreCase("recover"))
            return this.adminRecover(player, args[2]);
        this.plugin.messages().send(player, "grave.help");
        return true;
    }

    private boolean deathControl(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("emsichill.deathcontrol.admin")) {
            this.plugin.messages().send(sender, "general.no-permission");
            return true;
        }
        if (args.length != 2) {
            this.plugin.messages().send(sender, "grave.deathcontrol-usage");
            return true;
        }
        LossMode mode = LossMode.parse(args[1]);
        if (mode == null) {
            this.plugin.messages().send(sender, "grave.invalid-mode");
            return true;
        }
        if (args[0].equalsIgnoreCase("default")) {
            this.configFile.yaml().set("inventory-loss.default-mode", mode.name().toLowerCase(Locale.ROOT));
            this.configFile.save();
            this.plugin.messages().send(sender, "grave.default-mode-set", "{mode}", this.displayMode(mode));
        } else {
            this.repository.setMode(args[0], mode);
            this.repository.save();
            this.plugin.messages().send(sender, "grave.player-mode-set", "{player}", args[0],
                "{mode}", this.displayMode(mode));
        }
        return true;
    }

    @EventHandler
    // El modo elegido decide si se crea tumba, se conserva todo o se usa la caída vanilla.
    public void onDeath(final PlayerDeathEvent event) {
        if (!this.enabled()) return;
        Player player = event.getEntity();
        LossMode mode = this.repository.mode(player.getName(), this.defaultMode());
        if (mode == LossMode.DROP) return;
        if (mode == LossMode.KEEP) {
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setKeepLevel(true);
            event.setDroppedExp(0);
            this.plugin.messages().send(player, "grave.inventory-kept");
            return;
        }

        // getContents incluye almacenamiento, armadura y mano secundaria en su orden original.
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) items.add(item.clone());
        }
        int experience = Math.max(0, player.getTotalExperience());
        if (items.isEmpty() && experience == 0) return;

        Location deathLocation = player.getLocation().clone();
        Location marker = this.findMarkerLocation(deathLocation);
        String id = this.newId();
        long now = Instant.now().getEpochSecond();
        long lifetime = Math.max(60L, this.configFile.yaml().getLong("graves.lifetime-minutes", 30L) * 60L);
        long privateTime = Math.max(0L, this.configFile.yaml().getLong("graves.private-minutes", 5L) * 60L);
        Grave grave = new Grave(id, player.getUniqueId(), player.getName(), marker == null ? deathLocation : marker,
            items, experience, now, now + lifetime, now + privateTime, marker != null);
        this.repository.put(grave);
        if (!this.repository.saveNow()) {
            this.repository.remove(grave.id());
            this.plugin.getLogger().severe("Could not persist grave " + grave.id() + " for " + player.getName()
                + "; preserving vanilla death drops instead.");
            this.plugin.messages().send(player, "general.save-error");
            return;
        }

        // Suppress vanilla loss only after the complete recovery payload is safely on disk.
        event.setKeepInventory(false);
        event.setKeepLevel(false);
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setNewExp(0);
        event.setNewLevel(0);
        event.setNewTotalExp(0);
        if (marker != null) {
            try {
                this.placeMarker(grave);
                this.playGraveEffect(grave);
                this.plugin.rememberGraveBackLocation(player, marker);
            } catch (RuntimeException exception) {
                this.plugin.getLogger().warning("Grave " + grave.id() + " was saved, but its marker could not be "
                    + "displayed: " + exception.getMessage());
            }
        }
        this.plugin.messages().send(player, "grave.created", "{id}", id, "{world}", grave.world(),
            "{x}", Integer.toString(grave.x()), "{y}", Integer.toString(grave.y()), "{z}", Integer.toString(grave.z()));
    }

    // Finds a nearby empty block for the protected interaction point.
    private Location findMarkerLocation(final Location origin) {
        int maximumRadius = Math.max(1, Math.min(16,
            this.configFile.yaml().getInt("graves.marker-search-radius", 6)));
        int verticalRadius = Math.max(1, Math.min(8,
            this.configFile.yaml().getInt("graves.marker-vertical-radius", 4)));
        return GraveMarkerLocator.find(origin, maximumRadius, verticalRadius);
    }

    private void placeMarker(final Grave grave) {
        Location location = grave.location();
        if (location == null) return;
        location.getBlock().setType(Material.BARRIER, false);
        this.graveBlocks.put(this.blockKey(location), grave.id());
        this.placeGraveDisplays(grave);
    }

    private void placeGraveDisplays(final Grave grave) {
        this.removeGraveDisplays(grave.id());
        if (!this.configFile.yaml().getBoolean("graves.visuals.marker.enabled", true)) return;
        Location location = grave.location();
        if (location == null) return;

        List<Display> displays = new ArrayList<>();
        if (this.configFile.yaml().getBoolean("graves.name-display.enabled", true)) {
            displays.add(this.spawnTitleDisplay(grave, location));
        }
        if (this.configFile.yaml().getBoolean("graves.visuals.marker.base", true)) {
            Material base = this.blockMarkerMaterial("graves.visuals.marker.base-material",
                "POLISHED_DEEPSLATE", Material.COBBLESTONE);
            displays.add(this.spawnBlockDisplay(location.clone().add(0.05, 0.02, 0.18), base,
                new Vector3f(0.9F, 0.14F, 0.64F), 0.0F));
        }
        if (this.configFile.yaml().getBoolean("graves.visuals.marker.step", true)) {
            Material step = this.blockMarkerMaterial("graves.visuals.marker.step-material",
                "DEEPSLATE_TILES", Material.COBBLESTONE);
            displays.add(this.spawnBlockDisplay(location.clone().add(0.12, 0.16, 0.23), step,
                new Vector3f(0.76F, 0.13F, 0.54F), 0.0F));
        }
        if (this.configFile.yaml().getBoolean("graves.visuals.marker.headstone", true)) {
            Material headstone = this.blockMarkerMaterial("graves.visuals.marker.headstone-material",
                "DEEPSLATE_BRICKS", Material.COBBLESTONE);
            Material trim = this.blockMarkerMaterial("graves.visuals.marker.trim-material",
                "POLISHED_BLACKSTONE", Material.COBBLESTONE);
            displays.add(this.spawnBlockDisplay(location.clone().add(0.25, 0.27, 0.41), headstone,
                new Vector3f(0.5F, 0.7F, 0.18F), 0.0F));
            displays.add(this.spawnBlockDisplay(location.clone().add(0.18, 0.88, 0.39), headstone,
                new Vector3f(0.64F, 0.2F, 0.22F), 0.0F));
            displays.add(this.spawnBlockDisplay(location.clone().add(0.26, 1.06, 0.39), headstone,
                new Vector3f(0.48F, 0.2F, 0.22F), 0.0F));
            displays.add(this.spawnBlockDisplay(location.clone().add(0.34, 1.24, 0.4), trim,
                new Vector3f(0.32F, 0.13F, 0.2F), 0.0F));
        }
        if (this.configFile.yaml().getBoolean("graves.visuals.marker.plaque", true)) {
            Material plaque = this.blockMarkerMaterial("graves.visuals.marker.plaque-material",
                "CHISELED_DEEPSLATE", Material.COBBLESTONE);
            displays.add(this.spawnBlockDisplay(location.clone().add(0.34, 0.56, 0.365), plaque,
                new Vector3f(0.32F, 0.29F, 0.055F), 0.0F));
        }
        if (this.configFile.yaml().getBoolean("graves.visuals.marker.soul-lantern", true)) {
            Material lantern = this.itemMarkerMaterial("graves.visuals.marker.soul-lantern-material",
                "SOUL_LANTERN", Material.SOUL_LANTERN);
            displays.add(this.spawnItemDisplay(location.clone().add(0.5, 0.31, 0.19), lantern, 0.3F, false));
        }
        if (!displays.isEmpty()) this.graveDisplays.put(grave.id(), displays);
    }

    private TextDisplay spawnTitleDisplay(final Grave grave, final Location location) {
        double height = this.configFile.yaml().getDouble("graves.visuals.marker.title-height",
            this.configFile.yaml().getDouble("graves.name-display.height", 1.58));
        String title = this.configFile.yaml().getString("graves.visuals.marker.title",
            this.configFile.yaml().getString("graves.name-display.text", "{player}"));
        Component text = Component.text(this.formatGraveText(title, grave), NamedTextColor.GRAY);
        return location.getWorld().spawn(location.clone().add(0.5, height, 0.5), TextDisplay.class, entity -> {
            entity.text(text);
            entity.setAlignment(TextDisplay.TextAlignment.CENTER);
            entity.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setBrightness(new Display.Brightness(10, 10));
            entity.setLineWidth(120);
            entity.setSeeThrough(false);
            entity.setShadowed(true);
            entity.setTextOpacity((byte) 230);
            float scale = (float) this.configFile.yaml().getDouble("graves.visuals.marker.title-scale", 0.5D);
            entity.setTransformation(new Transformation(
                new Vector3f(),
                new AxisAngle4f(0.0F, 0.0F, 1.0F, 0.0F),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f(0.0F, 0.0F, 1.0F, 0.0F)
            ));
            this.prepareDisplay(entity, false);
        });
    }

    private BlockDisplay spawnBlockDisplay(
        final Location location,
        final Material material,
        final Vector3f scale,
        final float yawDegrees
    ) {
        return location.getWorld().spawn(location, BlockDisplay.class, entity -> {
            entity.setBlock(material.createBlockData());
            entity.setBrightness(new Display.Brightness(12, 15));
            entity.setTransformation(new Transformation(
                new Vector3f(),
                new AxisAngle4f((float) Math.toRadians(yawDegrees), 0.0F, 1.0F, 0.0F),
                scale,
                new AxisAngle4f(0.0F, 0.0F, 1.0F, 0.0F)
            ));
            this.prepareDisplay(entity, true, Display.Billboard.FIXED);
        });
    }

    private ItemDisplay spawnItemDisplay(
        final Location location,
        final Material material,
        final float scale,
        final boolean glowing
    ) {
        return location.getWorld().spawn(location, ItemDisplay.class, entity -> {
            entity.setItemStack(new ItemStack(material));
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            entity.setBrightness(new Display.Brightness(15, 15));
            entity.setGlowing(glowing);
            entity.setTransformation(new Transformation(
                new Vector3f(),
                new AxisAngle4f(0.0F, 0.0F, 1.0F, 0.0F),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f(0.0F, 0.0F, 1.0F, 0.0F)
            ));
            this.prepareDisplay(entity, true, Display.Billboard.CENTER);
        });
    }

    private void prepareDisplay(final Display display, final boolean smallViewRange) {
        this.prepareDisplay(display, smallViewRange, Display.Billboard.CENTER);
    }

    private void prepareDisplay(final Display display, final boolean smallViewRange, final Display.Billboard billboard) {
        display.setBillboard(billboard);
        display.setInvulnerable(true);
        display.setPersistent(false);
        display.setViewRange(smallViewRange ? 18.0F : 32.0F);
        display.setGravity(false);
    }

    private Material blockMarkerMaterial(final String path, final String preferred, final Material fallback) {
        Material material = this.markerMaterial(path, preferred, fallback);
        return material.isBlock() ? material : fallback;
    }

    private Material itemMarkerMaterial(final String path, final String preferred, final Material fallback) {
        return this.markerMaterial(path, preferred, fallback);
    }

    private Material markerMaterial(final String path, final String preferred, final Material fallback) {
        String configured = this.configFile.yaml().getString(path, preferred);
        Material material = configured == null ? null : Material.matchMaterial(configured);
        if (material == null) material = Material.matchMaterial(preferred);
        return material == null ? fallback : material;
    }

    private String formatGraveText(final String text, final Grave grave) {
        return text.replace("{player}", grave.ownerName()).replace("{id}", grave.id());
    }

    private void removeGraveDisplays(final String graveId) {
        List<Display> displays = this.graveDisplays.remove(graveId);
        if (displays == null) return;
        for (Display display : displays) if (display.isValid()) display.remove();
    }

    private void clearGraveDisplays() {
        for (List<Display> displays : this.graveDisplays.values()) {
            for (Display display : displays) if (display.isValid()) display.remove();
        }
        this.graveDisplays.clear();
    }

    private void restoreMarkers() {
        this.clearGraveDisplays();
        this.graveBlocks.clear();
        for (Grave grave : this.repository.all()) {
            this.restoreMarker(grave);
        }
    }

    private void restoreMarker(final Grave grave) {
        if (!grave.hasMarker()) return;
        Location location = grave.location();
        if (location == null) return;
        this.graveBlocks.put(this.blockKey(location), grave.id());
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        if (!location.getWorld().isChunkLoaded(chunkX, chunkZ)) return;
        Material markerType = location.getBlock().getType();
        if (markerType.isAir() || markerType == Material.CHEST) {
            location.getBlock().setType(Material.BARRIER, false);
        }
        this.placeGraveDisplays(grave);
    }

    @EventHandler
    public void onChunkLoad(final ChunkLoadEvent event) {
        if (!this.enabled()) return;
        String worldName = event.getChunk().getWorld().getName();
        int chunkX = event.getChunk().getX();
        int chunkZ = event.getChunk().getZ();
        for (Grave grave : this.repository.all()) {
            if (this.isGraveInChunk(grave, worldName, chunkX, chunkZ)) this.restoreMarker(grave);
        }
    }

    @EventHandler
    public void onChunkUnload(final ChunkUnloadEvent event) {
        if (!this.enabled()) return;
        String worldName = event.getChunk().getWorld().getName();
        int chunkX = event.getChunk().getX();
        int chunkZ = event.getChunk().getZ();
        for (Grave grave : this.repository.all()) {
            if (this.isGraveInChunk(grave, worldName, chunkX, chunkZ)) this.removeGraveDisplays(grave.id());
        }
    }

    private boolean isGraveInChunk(final Grave grave, final String worldName, final int chunkX, final int chunkZ) {
        return grave.hasMarker() && grave.world().equals(worldName)
            && (grave.x() >> 4) == chunkX && (grave.z() >> 4) == chunkZ;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(final PlayerInteractEvent event) {
        if (!this.enabled()) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        String id = this.graveBlocks.get(this.blockKey(event.getClickedBlock().getLocation()));
        if (id == null) return;
        event.setCancelled(true);
        if (event.getPlayer().isSneaking()) {
            this.collectDirectly(event.getPlayer(), id);
            return;
        }
        this.openGrave(event.getPlayer(), id);
    }

    private void openGrave(final Player player, final String id) {
        Grave grave = this.repository.get(id);
        if (grave == null) return;
        if (!this.canAccess(player, grave)) return;
        if (!this.openGraves.add(id)) {
            this.plugin.messages().send(player, "grave.already-open");
            return;
        }
        GraveHolder holder = new GraveHolder(id);
        Inventory inventory = Bukkit.createInventory(holder, 54, Component.text("Grave (" + grave.ownerName() + ")"));
        holder.setInventory(inventory);
        for (ItemStack item : grave.items()) inventory.addItem(item.clone());
        if (grave.experience() > 0) {
            int experience = grave.experience();
            grave.setExperience(0);
            if (!this.repository.saveNow()) {
                grave.setExperience(experience);
                this.openGraves.remove(id);
                this.plugin.messages().send(player, "general.save-error");
                return;
            }
            player.giveExp(experience);
        }
        player.openInventory(inventory);
    }

    private boolean canAccess(final Player player, final Grave grave) {
        boolean owner = grave.owner().equals(player.getUniqueId());
        boolean admin = player.hasPermission("emsichill.grave.admin");
        long privateSeconds = grave.publicAt() - Instant.now().getEpochSecond();
        if (!owner && !admin && privateSeconds > 0) {
            this.plugin.messages().send(player, "grave.private", "{player}", grave.ownerName(),
                "{time}", this.formatRemaining(privateSeconds));
            return false;
        }
        return true;
    }

    private void collectDirectly(final Player player, final String id) {
        Grave grave = this.repository.get(id);
        if (grave == null || !this.canAccess(player, grave)) return;
        if (!this.openGraves.add(id)) {
            this.plugin.messages().send(player, "grave.already-open");
            return;
        }
        RecoveryPayload payload = this.detachForRecovery(grave);
        if (payload == null) {
            this.plugin.messages().send(player, "general.save-error");
            return;
        }
        boolean droppedOverflow = this.deliverItems(player, payload.items());
        if (payload.experience() > 0) player.giveExp(payload.experience());
        this.plugin.messages().send(player, "grave.collected-all");
        if (droppedOverflow) this.plugin.messages().send(player, "grave.overflow-dropped");
    }

    @EventHandler
    public void onInventoryClick(final InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof GraveHolder holder) {
            if (event.isShiftClick() && event.getWhoClicked() instanceof Player player) {
                event.setCancelled(true);
                this.collectAll(player, holder, event.getView().getTopInventory());
                return;
            }
            Bukkit.getScheduler().runTask(this.plugin, () -> this.syncOpenInventory(holder, event.getView().getTopInventory()));
        }
    }

    // Shift + clic entrega todo; el sobrante se deja junto al jugador para no perderlo.
    private void collectAll(final Player player, final GraveHolder holder, final Inventory inventory) {
        Grave grave = this.repository.get(holder.id());
        if (grave == null) return;
        List<ItemStack> inventoryItems = new ArrayList<>();
        for (ItemStack item : inventory.getContents()) {
            if (item != null && !item.getType().isAir()) inventoryItems.add(item.clone());
        }
        grave.items().clear();
        grave.items().addAll(inventoryItems);
        RecoveryPayload payload = this.detachForRecovery(grave);
        if (payload == null) {
            this.plugin.messages().send(player, "general.save-error");
            return;
        }
        boolean droppedOverflow = this.deliverItems(player, payload.items());
        if (payload.experience() > 0) player.giveExp(payload.experience());
        inventory.clear();
        player.closeInventory();
        this.plugin.messages().send(player, "grave.collected-all");
        if (droppedOverflow) this.plugin.messages().send(player, "grave.overflow-dropped");
    }

    private boolean deliverItems(final Player player, final Iterable<ItemStack> items) {
        boolean droppedOverflow = false;
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) continue;
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(item.clone());
            for (ItemStack remaining : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), remaining);
                droppedOverflow = true;
            }
        }
        return droppedOverflow;
    }

    @EventHandler
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof GraveHolder holder) {
            Bukkit.getScheduler().runTask(this.plugin, () -> this.syncOpenInventory(holder, event.getView().getTopInventory()));
        }
    }

    private void syncOpenInventory(final GraveHolder holder, final Inventory inventory) {
        Grave grave = this.repository.get(holder.id());
        if (grave == null) return;
        grave.items().clear();
        for (ItemStack item : inventory.getContents())
            if (item != null && !item.getType().isAir()) grave.items().add(item.clone());
        this.repository.save();
    }

    @EventHandler
    public void onClose(final InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof GraveHolder holder)) return;
        Grave grave = this.repository.get(holder.id());
        this.openGraves.remove(holder.id());
        if (grave == null) return;
        grave.items().clear();
        for (ItemStack item : event.getInventory().getContents())
            if (item != null && !item.getType().isAir()) grave.items().add(item.clone());
        if (grave.items().isEmpty() && grave.experience() == 0) this.removeGrave(grave, false);
        else this.repository.saveNow();
    }

    private boolean listGraves(final Player player) {
        List<Grave> owned = new ArrayList<>();
        for (Grave grave : this.repository.all()) if (grave.owner().equals(player.getUniqueId())) owned.add(grave);
        if (owned.isEmpty()) {
            this.plugin.messages().send(player, "grave.none");
            return true;
        }
        owned.sort((first, second) -> Long.compare(second.createdAt(), first.createdAt()));
        this.plugin.messages().send(player, "grave.list-header", "{count}", Integer.toString(owned.size()));
        long now = Instant.now().getEpochSecond();
        for (Grave grave : owned) {
            this.plugin.messages().send(player, "grave.list-entry", "{id}", grave.id(),
                "{dimension}", this.dimensionName(grave.world()), "{world}", grave.world(),
                "{x}", Integer.toString(grave.x()), "{y}", Integer.toString(grave.y()),
                "{z}", Integer.toString(grave.z()), "{time}", this.formatRemaining(grave.expiresAt() - now));
        }
        return true;
    }

    private String dimensionName(final String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return worldName;
        return switch (world.getEnvironment()) {
            case NORMAL -> "Overworld";
            case NETHER -> "Nether";
            case THE_END -> "End";
            default -> worldName;
        };
    }

    private String formatRemaining(final long totalSeconds) {
        long seconds = Math.max(0L, totalSeconds);
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long remainder = seconds % 60L;
        if (hours > 0) return hours + " h " + minutes + " min";
        if (minutes > 0) return minutes + " min " + remainder + " s";
        return remainder + " s";
    }

    private boolean locate(final Player player, final String partialId) {
        List<Grave> matches = new ArrayList<>();
        Grave owned = this.findOwnedGrave(player, partialId);
        if (owned != null) matches.add(owned);
        if (matches.isEmpty() && player.hasPermission("emsichill.grave.admin")) {
            matches.addAll(this.findGravesByOwnerName(partialId));
            if (matches.isEmpty()) {
                Grave grave = this.findGrave(partialId);
                if (grave != null) matches.add(grave);
            }
        }
        if (matches.isEmpty()) {
            this.plugin.messages().send(player, "grave.not-found");
            return true;
        }
        matches.sort((first, second) -> Long.compare(second.createdAt(), first.createdAt()));
        if (matches.size() > 1) {
            this.plugin.messages().send(player, "grave.location-header", "{count}", Integer.toString(matches.size()),
                "{player}", matches.getFirst().ownerName());
        }
        for (Grave grave : matches) {
            this.plugin.messages().send(player, "grave.location", "{id}", grave.id(), "{player}", grave.ownerName(),
                "{world}", grave.world(), "{x}", Integer.toString(grave.x()), "{y}", Integer.toString(grave.y()),
                "{z}", Integer.toString(grave.z()));
        }
        this.highlightGrave(player, matches.getFirst());
        return true;
    }

    private boolean recoverById(final Player actor, final String partialId, final Player receiver) {
        Grave grave = this.findOwnedGrave(actor, partialId);
        if (grave == null && actor.hasPermission("emsichill.grave.admin")) {
            Player target = Bukkit.getPlayerExact(partialId);
            if (target != null) return this.recoverPlayerGraves(actor, target);
            List<Grave> namedGraves = this.findGravesByOwnerName(partialId);
            if (!namedGraves.isEmpty()) {
                this.plugin.messages().send(actor, "grave.player-offline");
                return true;
            }
            grave = this.findGrave(partialId);
        }
        if (grave == null) {
            this.plugin.messages().send(actor, "grave.not-found");
            return true;
        }
        if (!this.recover(grave, receiver)) return true;
        this.plugin.messages().send(actor, "grave.recovered", "{id}", grave.id(), "{player}", receiver.getName());
        return true;
    }

    private boolean adminRecover(final Player admin, final String playerName) {
        if (!admin.hasPermission("emsichill.grave.admin")) {
            this.plugin.messages().send(admin, "general.no-permission");
            return true;
        }
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            this.plugin.messages().send(admin, "grave.player-offline");
            return true;
        }
        return this.recoverPlayerGraves(admin, target);
    }

    private boolean recoverPlayerGraves(final Player admin, final Player target) {
        List<Grave> owned = this.findGravesByOwner(target.getUniqueId());
        int recovered = 0;
        for (Grave grave : owned) if (this.recover(grave, target)) recovered++;
        this.plugin.messages().send(admin, "grave.admin-recovered", "{count}", Integer.toString(recovered),
            "{player}", target.getName());
        return true;
    }

    private boolean recover(final Grave grave, final Player receiver) {
        if (!this.openGraves.add(grave.id())) {
            this.plugin.messages().send(receiver, "grave.already-open");
            return false;
        }
        RecoveryPayload payload = this.detachForRecovery(grave);
        if (payload == null) {
            this.plugin.messages().send(receiver, "general.save-error");
            return false;
        }
        this.deliverItems(receiver, payload.items());
        if (payload.experience() > 0) receiver.giveExp(payload.experience());
        return true;
    }

    private RecoveryPayload detachForRecovery(final Grave grave) {
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack item : grave.items()) items.add(item.clone());
        int experience = grave.experience();
        this.repository.remove(grave.id());
        if (!this.repository.saveNow()) {
            this.repository.put(grave);
            this.openGraves.remove(grave.id());
            return null;
        }
        grave.items().clear();
        grave.setExperience(0);
        this.cleanupMarker(grave);
        this.openGraves.remove(grave.id());
        return new RecoveryPayload(items, experience);
    }

    private void cleanupMarker(final Grave grave) {
        this.removeGraveDisplays(grave.id());
        Location location = grave.location();
        if (location == null) return;
        this.graveBlocks.remove(this.blockKey(location));
        Material markerType = location.getBlock().getType();
        if (grave.hasMarker() && (markerType == Material.BARRIER || markerType == Material.CHEST)) {
            location.getBlock().setType(Material.AIR, false);
        }
    }

    // La expiración aplica la política configurada antes de eliminar el marcador.
    private void expireGraves() {
        if (!this.enabled()) return;
        long now = Instant.now().getEpochSecond();
        for (Grave grave : new ArrayList<>(this.repository.all())) {
            if (grave.expiresAt() > now || this.openGraves.contains(grave.id())) continue;
            boolean drop = this.configFile.yaml().getString("graves.on-expire", "drop").equalsIgnoreCase("drop");
            this.removeGrave(grave, drop);
        }
    }

    private void removeGrave(final Grave grave, final boolean dropItems) {
        this.repository.remove(grave.id());
        this.openGraves.remove(grave.id());
        Location location = grave.location();
        if (location != null) {
            if (dropItems) for (ItemStack item : grave.items()) location.getWorld().dropItemNaturally(location, item);
        }
        this.cleanupMarker(grave);
        this.repository.save();
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(final BlockBreakEvent event) {
        if (!this.enabled()) return;
        if (this.graveBlocks.containsKey(this.blockKey(event.getBlock().getLocation()))) {
            event.setCancelled(true);
            this.plugin.messages().send(event.getPlayer(), "grave.use-command");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplosion(final EntityExplodeEvent event) {
        if (!this.enabled()) return;
        event.blockList().removeIf(block -> this.graveBlocks.containsKey(this.blockKey(block.getLocation())));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplosion(final BlockExplodeEvent event) {
        if (!this.enabled()) return;
        event.blockList().removeIf(block -> this.graveBlocks.containsKey(this.blockKey(block.getLocation())));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(final BlockPistonExtendEvent event) {
        if (!this.enabled()) return;
        if (event.getBlocks().stream().anyMatch(block -> this.graveBlocks.containsKey(this.blockKey(block.getLocation())))) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(final BlockPistonRetractEvent event) {
        if (!this.enabled()) return;
        if (event.getBlocks().stream().anyMatch(block -> this.graveBlocks.containsKey(this.blockKey(block.getLocation())))) event.setCancelled(true);
    }

    private Grave findOwnedGrave(final Player player, final String partial) {
        for (Grave grave : this.repository.all())
            if (grave.owner().equals(player.getUniqueId()) && grave.id().toLowerCase(Locale.ROOT).startsWith(partial.toLowerCase(Locale.ROOT))) return grave;
        return null;
    }

    private List<Grave> findGravesByOwner(final UUID owner) {
        List<Grave> matches = new ArrayList<>();
        for (Grave grave : this.repository.all()) if (grave.owner().equals(owner)) matches.add(grave);
        matches.sort((first, second) -> Long.compare(second.createdAt(), first.createdAt()));
        return matches;
    }

    private List<Grave> findGravesByOwnerName(final String playerName) {
        String requested = playerName.toLowerCase(Locale.ROOT);
        List<Grave> exact = new ArrayList<>();
        List<Grave> partial = new ArrayList<>();
        for (Grave grave : this.repository.all()) {
            String ownerName = grave.ownerName().toLowerCase(Locale.ROOT);
            if (ownerName.equals(requested)) exact.add(grave);
            else if (ownerName.startsWith(requested)) partial.add(grave);
        }
        List<Grave> matches = exact.isEmpty() ? partial : exact;
        matches.sort((first, second) -> Long.compare(second.createdAt(), first.createdAt()));
        return matches;
    }

    private Grave findGrave(final String partial) {
        for (Grave grave : this.repository.all()) if (grave.id().startsWith(partial)) return grave;
        return null;
    }

    private void playGraveEffect(final Grave grave) {
        Location location = grave.location();
        if (location == null || !this.configFile.yaml().getBoolean("graves.visuals.effects", true)) return;
        Location center = location.clone().add(0.5, 0.8, 0.5);
        World world = location.getWorld();
        world.spawnParticle(Particle.SOUL, center, 12, 0.22, 0.35, 0.22, 0.01);
        world.spawnParticle(Particle.END_ROD, center, 7, 0.18, 0.3, 0.18, 0.005);
        world.playSound(center, Sound.BLOCK_SOUL_SAND_PLACE, 0.45F, 0.85F);
    }

    private void highlightGrave(final Player viewer, final Grave grave) {
        Location location = grave.location();
        if (location == null || !this.configFile.yaml().getBoolean("graves.visuals.locate-marker", true)) return;
        int seconds = Math.max(1, this.configFile.yaml().getInt("graves.visuals.locate-marker-seconds", 8));
        int interval = 10;
        int durationTicks = seconds * 20;
        new BukkitRunnable() {
            private int elapsed;

            @Override
            public void run() {
                if (!viewer.isOnline() || this.elapsed > durationTicks) {
                    this.cancel();
                    return;
                }
                Location center = location.clone().add(0.5, 1.15, 0.5);
                Particle.DustOptions cyan = new Particle.DustOptions(Color.fromRGB(95, 205, 220), 1.05F);
                double spin = Math.toRadians(this.elapsed * 10.0D);
                for (int index = 0; index < 12; index++) {
                    double angle = spin + Math.toRadians(index * 30.0D);
                    double radius = 0.68D;
                    double height = 0.15D + ((this.elapsed + index * 3) % 30) / 30.0D * 1.2D;
                    Location point = location.clone().add(
                        0.5 + Math.cos(angle) * radius,
                        height,
                        0.5 + Math.sin(angle) * radius
                    );
                    viewer.spawnParticle(Particle.DUST, point, 1, 0.0, 0.0, 0.0, 0.0, cyan);
                }
                viewer.spawnParticle(Particle.SOUL, center, 4, 0.15, 0.28, 0.15, 0.01);
                if (this.elapsed == 0) viewer.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.45F, 1.1F);
                this.elapsed += interval;
            }
        }.runTaskTimer(this.plugin, 0L, interval);
    }

    private String newId() {
        String id;
        do id = UUID.randomUUID().toString().substring(0, 8); while (this.repository.contains(id));
        return id;
    }

    private String blockKey(final Location location) {
        return location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private LossMode defaultMode() {
        LossMode value = LossMode.parse(this.configFile.yaml().getString("inventory-loss.default-mode", "grave"));
        return value == null ? LossMode.GRAVE : value;
    }

    private boolean enabled() {
        return this.plugin.moduleEnabled("graves");
    }

    private String displayMode(final LossMode mode) {
        return switch (mode) {
            case GRAVE -> "grave";
            case KEEP -> "keep";
            case DROP -> "drop";
        };
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args) {
        if (command.getName().equalsIgnoreCase("deathcontrol")) {
            if (!sender.hasPermission("emsichill.deathcontrol.admin")) return Collections.emptyList();
            if (args.length == 1) {
                List<String> values = new ArrayList<>(List.of("default"));
                for (Player player : Bukkit.getOnlinePlayers()) values.add(player.getName());
                return CommandSuggestions.filter(values, args[0]);
            }
            if (args.length == 2) return CommandSuggestions.filter(List.of("grave", "keep", "drop"), args[1]);
        } else {
            if (!sender.hasPermission("emsichill.grave.admin")) return Collections.emptyList();
            if (args.length == 1) {
                List<String> actions = new ArrayList<>(List.of("list", "locate", "recover", "admin"));
                return CommandSuggestions.filter(actions, args[0]);
            }
            if (args.length == 2 && (args[0].equalsIgnoreCase("locate") || args[0].equalsIgnoreCase("recover"))) {
                List<String> ids = new ArrayList<>();
                if (sender instanceof Player player) {
                    for (Grave grave : this.repository.all()) if (grave.owner().equals(player.getUniqueId())) ids.add(grave.id());
                }
                if (sender.hasPermission("emsichill.grave.admin")) {
                    for (Player player : Bukkit.getOnlinePlayers()) ids.add(player.getName());
                    for (Grave grave : this.repository.all()) ids.add(grave.ownerName());
                }
                return CommandSuggestions.filter(ids, args[1]);
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("admin") && sender.hasPermission("emsichill.grave.admin")) {
                return CommandSuggestions.filter(List.of("recover"), args[1]);
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("admin")
                && args[1].equalsIgnoreCase("recover") && sender.hasPermission("emsichill.grave.admin")) {
                List<String> names = new ArrayList<>();
                for (Player player : Bukkit.getOnlinePlayers()) names.add(player.getName());
                return CommandSuggestions.filter(names, args[2]);
            }
        }
        return Collections.emptyList();
    }

    private static final class GraveHolder implements InventoryHolder {
        private final String id; private Inventory inventory;
        private GraveHolder(final String id) { this.id = id; }
        private String id() { return this.id; } private void setInventory(final Inventory value) { this.inventory = value; }
        @Override public Inventory getInventory() { return this.inventory; }
    }

    private record RecoveryPayload(List<ItemStack> items, int experience) {
    }
}
