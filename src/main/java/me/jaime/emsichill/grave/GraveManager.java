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
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.entity.Display;
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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import me.jaime.emsichill.Main;
import me.jaime.emsichill.config.ConfigFile;
import me.jaime.emsichill.util.CommandSuggestions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Controla muertes, cofres de tumba, recogida de objetos, marcadores visibles y protección del
 * bloque mientras la tumba permanece activa.
 */
public final class GraveManager implements CommandExecutor, TabCompleter, Listener {
    private final Main plugin;
    private final GraveRepository repository;
    private final Map<String, String> graveBlocks = new ConcurrentHashMap<>();
    private final Map<String, TextDisplay> graveDisplays = new ConcurrentHashMap<>();
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
    }

    public void start() {
        if (this.cleanupTask != null) this.cleanupTask.cancel();
        this.cleanupTask = Bukkit.getScheduler().runTaskTimer(this.plugin, this::expireGraves, 1200L, 1200L);
        this.restoreMarkers();
    }

    public void stop() {
        if (this.cleanupTask != null) this.cleanupTask.cancel();
        for (TextDisplay display : this.graveDisplays.values()) if (display.isValid()) display.remove();
        this.graveDisplays.clear();
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
        if (!this.plugin.moduleEnabled("graves")) return;
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
        event.setKeepInventory(false);
        event.setKeepLevel(false);
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setNewExp(0);
        event.setNewLevel(0);
        event.setNewTotalExp(0);
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
        if (marker != null) {
            this.placeMarker(grave);
            this.plugin.rememberGraveBackLocation(player, marker);
        }
        this.repository.save();
        this.plugin.messages().send(player, "grave.created", "{id}", id, "{world}", grave.world(),
            "{x}", Integer.toString(grave.x()), "{y}", Integer.toString(grave.y()), "{z}", Integer.toString(grave.z()));
    }

    // Busca un bloque cercano que pueda alojar el cofre sin reemplazar construcciones.
    private Location findMarkerLocation(final Location origin) {
        World world = origin.getWorld();
        if (world == null) return null;
        int maximumRadius = Math.max(1, Math.min(16,
            this.configFile.yaml().getInt("graves.marker-search-radius", 6)));
        int verticalRadius = Math.max(1, Math.min(8,
            this.configFile.yaml().getInt("graves.marker-vertical-radius", 4)));
        for (int radius = 0; radius <= maximumRadius; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    int bx = origin.getBlockX() + x; int bz = origin.getBlockZ() + z;
                    for (int distance = 0; distance <= verticalRadius; distance++) {
                        int[] offsets = distance == 0 ? new int[] {0} : new int[] {distance, -distance};
                        for (int yOffset : offsets) {
                            int by = origin.getBlockY() + yOffset;
                            if (by <= world.getMinHeight() || by >= world.getMaxHeight() - 1) continue;
                            Block target = world.getBlockAt(bx, by, bz);
                            if (this.canPlaceMarker(target)) return target.getLocation();
                        }
                    }
                }
            }
        }
        return null;
    }

    private boolean canPlaceMarker(final Block target) {
        Block above = target.getRelative(0, 1, 0);
        Block head = target.getRelative(0, 2, 0);
        Block floor = target.getRelative(0, -1, 0);
        Material floorType = floor.getType();
        return target.isPassable() && !target.isLiquid() && above.isPassable() && !above.isLiquid()
            && head.isPassable() && !head.isLiquid()
            && floorType.isSolid() && floorType != Material.MAGMA_BLOCK && floorType != Material.CACTUS
            && floorType != Material.CAMPFIRE && floorType != Material.SOUL_CAMPFIRE
            && target.getWorld().getWorldBorder().isInside(target.getLocation());
    }

    private void placeMarker(final Grave grave) {
        Location location = grave.location();
        if (location == null) return;
        location.getBlock().setType(Material.CHEST, false);
        if (location.getBlock().getState() instanceof Chest chest) {
            chest.customName(Component.text("Tumba (" + grave.ownerName() + ")"));
            chest.update(true);
        }
        this.graveBlocks.put(this.blockKey(location), grave.id());
        this.placeNameDisplay(grave);
    }

    private void placeNameDisplay(final Grave grave) {
        this.removeNameDisplay(grave.id());
        if (!this.configFile.yaml().getBoolean("graves.name-display.enabled", true)) return;
        Location location = grave.location();
        if (location == null) return;
        double height = this.configFile.yaml().getDouble("graves.name-display.height", 1.45);
        String format = this.configFile.yaml().getString("graves.name-display.text", "Tumba de {player}");
        String text = format.replace("{player}", grave.ownerName());
        TextDisplay display = location.getWorld().spawn(location.clone().add(0.5, height, 0.5), TextDisplay.class, entity -> {
            entity.text(Component.text(text, NamedTextColor.YELLOW));
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setSeeThrough(true);
            entity.setShadowed(true);
            entity.setInvulnerable(true);
            entity.setPersistent(false);
        });
        this.graveDisplays.put(grave.id(), display);
    }

    private void removeNameDisplay(final String graveId) {
        TextDisplay display = this.graveDisplays.remove(graveId);
        if (display != null && display.isValid()) display.remove();
    }

    private void restoreMarkers() {
        for (TextDisplay display : this.graveDisplays.values()) if (display.isValid()) display.remove();
        this.graveDisplays.clear();
        this.graveBlocks.clear();
        for (Grave grave : this.repository.all()) {
            if (!grave.hasMarker()) continue;
            Location location = grave.location();
            if (location != null) {
                if (location.getBlock().getType().isAir()) location.getBlock().setType(Material.CHEST, false);
                if (location.getBlock().getState() instanceof Chest chest) {
                    chest.customName(Component.text("Tumba (" + grave.ownerName() + ")"));
                    chest.update(true);
                }
                this.graveBlocks.put(this.blockKey(location), grave.id());
                this.placeNameDisplay(grave);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(final PlayerInteractEvent event) {
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
        Inventory inventory = Bukkit.createInventory(holder, 54, Component.text("Tumba (" + grave.ownerName() + ")"));
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
        this.repository.saveNow();
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
            case NORMAL -> "Mundo normal";
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
        Grave grave = this.findOwnedGrave(player, partialId);
        if (grave == null) this.plugin.messages().send(player, "grave.not-found");
        else this.plugin.messages().send(player, "grave.location", "{id}", grave.id(), "{world}", grave.world(),
            "{x}", Integer.toString(grave.x()), "{y}", Integer.toString(grave.y()), "{z}", Integer.toString(grave.z()));
        return true;
    }

    private boolean recoverById(final Player actor, final String partialId, final Player receiver) {
        Grave grave = this.findOwnedGrave(actor, partialId);
        if (grave == null && actor.hasPermission("emsichill.grave.admin")) grave = this.findGrave(partialId);
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
        List<Grave> owned = new ArrayList<>();
        for (Grave grave : this.repository.all()) if (grave.owner().equals(target.getUniqueId())) owned.add(grave);
        int recovered = 0;
        for (Grave grave : owned) if (this.recover(grave, target)) recovered++;
        this.plugin.messages().send(admin, "grave.admin-recovered", "{count}", Integer.toString(recovered), "{player}", target.getName());
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
        this.removeNameDisplay(grave.id());
        Location location = grave.location();
        if (location == null) return;
        this.graveBlocks.remove(this.blockKey(location));
        if (grave.hasMarker() && location.getBlock().getType() == Material.CHEST) {
            location.getBlock().setType(Material.AIR, false);
        }
    }

    // La expiración aplica la política configurada antes de eliminar el marcador.
    private void expireGraves() {
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
        if (this.graveBlocks.containsKey(this.blockKey(event.getBlock().getLocation()))) {
            event.setCancelled(true);
            this.plugin.messages().send(event.getPlayer(), "grave.use-command");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplosion(final EntityExplodeEvent event) {
        event.blockList().removeIf(block -> this.graveBlocks.containsKey(this.blockKey(block.getLocation())));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplosion(final BlockExplodeEvent event) {
        event.blockList().removeIf(block -> this.graveBlocks.containsKey(this.blockKey(block.getLocation())));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(final BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> this.graveBlocks.containsKey(this.blockKey(block.getLocation())))) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(final BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> this.graveBlocks.containsKey(this.blockKey(block.getLocation())))) event.setCancelled(true);
    }

    private Grave findOwnedGrave(final Player player, final String partial) {
        for (Grave grave : this.repository.all())
            if (grave.owner().equals(player.getUniqueId()) && grave.id().toLowerCase(Locale.ROOT).startsWith(partial.toLowerCase(Locale.ROOT))) return grave;
        return null;
    }

    private Grave findGrave(final String partial) {
        for (Grave grave : this.repository.all()) if (grave.id().startsWith(partial)) return grave;
        return null;
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

    private String displayMode(final LossMode mode) {
        return switch (mode) {
            case GRAVE -> "tumba";
            case KEEP -> "conservar";
            case DROP -> "soltar";
        };
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args) {
        if (command.getName().equalsIgnoreCase("deathcontrol")) {
            if (args.length == 1) {
                List<String> values = new ArrayList<>(List.of("default"));
                for (Player player : Bukkit.getOnlinePlayers()) values.add(player.getName());
                return CommandSuggestions.filter(values, args[0]);
            }
            if (args.length == 2) return CommandSuggestions.filter(List.of("grave", "keep", "drop"), args[1]);
        } else {
            if (args.length == 1) return CommandSuggestions.filter(List.of("list", "locate", "recover", "admin"), args[0]);
            if (args.length == 2 && (args[0].equalsIgnoreCase("locate") || args[0].equalsIgnoreCase("recover"))) {
                List<String> ids = new ArrayList<>();
                if (sender instanceof Player player) {
                    for (Grave grave : this.repository.all()) if (grave.owner().equals(player.getUniqueId())) ids.add(grave.id());
                }
                return CommandSuggestions.filter(ids, args[1]);
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("admin")) return CommandSuggestions.filter(List.of("recover"), args[1]);
            if (args.length == 3 && args[0].equalsIgnoreCase("admin")
                && args[1].equalsIgnoreCase("recover")) {
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