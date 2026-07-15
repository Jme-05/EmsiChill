package me.jaime.emsichill.staff;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import me.jaime.emsichill.Main;
import me.jaime.emsichill.config.ConfigFile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Coordina staffchat, vanish y modo staff, incluidas sus herramientas y restricciones mientras
 * un moderador trabaja oculto.
 */
public final class StaffManager implements CommandExecutor, TabCompleter, Listener {
    private final Main plugin;
    private final File dataFile;
    private final NamespacedKey toolKey;
    private final Set<String> vanished = ConcurrentHashMap.newKeySet();
    private final Set<UUID> staffChatEnabled = ConcurrentHashMap.newKeySet();
    private final Map<String, StaffSnapshot> snapshots = new ConcurrentHashMap<>();
    private ConfigFile configFile;

    public StaffManager(final Main plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "Staff/data.yml");
        this.toolKey = new NamespacedKey(plugin, "staff_tool");
        this.reloadConfiguration();
        this.loadData();
    }

    public void reloadConfiguration() {
        if (this.configFile == null) this.configFile = new ConfigFile(this.plugin, "Staff/config.yml");
        else this.configFile.reload();
        this.refreshVisibility();
    }

    public void start() {
        for (Player player : Bukkit.getOnlinePlayers()) this.handleJoin(player);
    }

    public void stop() {
        for (Player player : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            if (this.snapshots.containsKey(this.key(player))) this.disableStaffMode(player);
        }
        this.saveData();
    }

    public int vanishedCount() { return this.vanished.size(); }
    public void persistData() { this.saveData(); }

    public boolean isVanished(final Player player) {
        return this.vanished.contains(this.key(player));
    }

    // La visibilidad se recalcula por espectador para respetar el permiso de ver ocultos.
    public void refreshVisibility() {
        if (!this.plugin.moduleEnabled("staff")) return;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (!this.isVanished(target)) continue;
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (viewer.equals(target)) continue;
                if (viewer.hasPermission("emsichill.vanish.see")) viewer.showPlayer(this.plugin, target);
                else viewer.hidePlayer(this.plugin, target);
            }
        }
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (!this.plugin.moduleEnabled("staff")) {
            this.plugin.messages().send(sender, "general.module-disabled");
            return true;
        }
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "staffchat" -> this.staffChat(sender, args);
            case "vanish" -> this.vanish(sender, args);
            case "vanishlist" -> this.vanishList(sender);
            case "staffmode" -> this.staffMode(sender, args);
            default -> true;
        };
    }

    private boolean staffChat(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("emsichill.staffchat")) {
            this.plugin.messages().send(sender, "general.no-permission");
            return true;
        }
        if (args.length == 0 || (args.length == 1 && args[0].equalsIgnoreCase("toggle"))) {
            if (!(sender instanceof Player player)) {
                this.plugin.messages().send(sender, "staff.staffchat-usage");
                return true;
            }
            boolean enabled = !this.staffChatEnabled.remove(player.getUniqueId());
            if (enabled) this.staffChatEnabled.add(player.getUniqueId());
            this.plugin.messages().send(player, enabled ? "staff.staffchat-on" : "staff.staffchat-off");
            return true;
        }
        this.sendStaffMessage(sender, String.join(" ", args));
        return true;
    }

    private void sendStaffMessage(final CommandSender sender, final String text) {
        Component message = Component.text("[Staff] ", NamedTextColor.AQUA)
            .append(Component.text(sender.getName() + ": ", NamedTextColor.YELLOW))
            .append(Component.text(text, NamedTextColor.WHITE));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("emsichill.staffchat")) player.sendMessage(message);
        }
        if (this.configFile.yaml().getBoolean("staffchat.show-in-console", true)) Bukkit.getConsoleSender().sendMessage(message);
    }

    private boolean vanish(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("emsichill.vanish")) {
            this.plugin.messages().send(sender, "general.no-permission");
            return true;
        }
        Player target;
        if (args.length == 0 && sender instanceof Player player) target = player;
        else if (args.length == 1 && sender.hasPermission("emsichill.vanish.others")) target = this.findOnline(args[0]);
        else {
            this.plugin.messages().send(sender, "staff.vanish-usage");
            return true;
        }
        if (target == null) {
            this.plugin.messages().send(sender, "staff.player-not-found");
            return true;
        }
        this.setVanish(target, !this.isVanished(target));
        this.plugin.messages().send(target, this.isVanished(target) ? "staff.vanish-on" : "staff.vanish-off");
        if (!sender.equals(target)) this.plugin.messages().send(sender, "staff.vanish-other", "{player}", target.getName());
        return true;
    }

    private void setVanish(final Player target, final boolean enabled) {
        if (enabled) this.vanished.add(this.key(target));
        else this.vanished.remove(this.key(target));
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(target)) continue;
            if (enabled && !viewer.hasPermission("emsichill.vanish.see")) viewer.hidePlayer(this.plugin, target);
            else viewer.showPlayer(this.plugin, target);
        }
        target.setCanPickupItems(!enabled || !this.configFile.yaml().getBoolean("vanish.disable-item-pickup", true));
        this.saveData();
    }

    private boolean vanishList(final CommandSender sender) {
        if (!sender.hasPermission("emsichill.vanish.see")) {
            this.plugin.messages().send(sender, "general.no-permission");
            return true;
        }
        this.plugin.messages().send(sender, "staff.vanish-list", "{players}", this.vanished.isEmpty() ? "-" : String.join(", ", this.vanished));
        return true;
    }

    private boolean staffMode(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("emsichill.staffmode")) {
            this.plugin.messages().send(sender, "general.no-permission");
            return true;
        }
        Player target;
        if (args.length == 0 && sender instanceof Player player) target = player;
        else if (args.length == 1 && sender.hasPermission("emsichill.staffmode.others")) target = this.findOnline(args[0]);
        else {
            this.plugin.messages().send(sender, "staff.staffmode-usage");
            return true;
        }
        if (target == null) {
            this.plugin.messages().send(sender, "staff.player-not-found");
            return true;
        }
        if (this.snapshots.containsKey(this.key(target))) this.disableStaffMode(target);
        else this.enableStaffMode(target);
        if (!sender.equals(target)) this.plugin.messages().send(sender, "staff.staffmode-other", "{player}", target.getName());
        return true;
    }

    // Se guarda una instantánea completa antes de reemplazar inventario, modo y vanish.
    private void enableStaffMode(final Player player) {
        StaffSnapshot snapshot = StaffSnapshot.capture(player, this.isVanished(player));
        this.snapshots.put(this.key(player), snapshot);
        this.saveData();
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(new ItemStack[4]);
        inventory.setItem(0, this.tool(Material.COMPASS, "random", "Jugador aleatorio"));
        inventory.setItem(1, this.tool(Material.PLAYER_HEAD, "inventory", "Inspeccionar inventario"));
        inventory.setItem(2, this.tool(Material.ENDER_CHEST, "ender", "Inspeccionar cofre de Ender"));
        inventory.setItem(7, this.tool(Material.LIME_DYE, "vanish", "Alternar ocultación"));
        GameMode mode;
        try {
            mode = GameMode.valueOf(this.configFile.yaml().getString("staffmode.game-mode", "SPECTATOR").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            mode = GameMode.SPECTATOR;
        }
        player.setGameMode(mode);
        if (this.configFile.yaml().getBoolean("staffmode.enable-vanish", true)) this.setVanish(player, true);
        this.plugin.messages().send(player, "staff.staffmode-on");
    }

    private void disableStaffMode(final Player player) {
        StaffSnapshot snapshot = this.snapshots.remove(this.key(player));
        if (snapshot == null) return;
        snapshot.restore(player);
        if (this.configFile.yaml().getBoolean("staffmode.disable-vanish-on-exit", true)) this.setVanish(player, snapshot.vanishedBefore());
        this.saveData();
        this.plugin.messages().send(player, "staff.staffmode-off");
    }

    private ItemStack tool(final Material material, final String id, final String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.AQUA));
        meta.getPersistentDataContainer().set(this.toolKey, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    private String toolId(final ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(this.toolKey, PersistentDataType.STRING);
    }

    @EventHandler(ignoreCancelled = true)
    // Las herramientas se identifican por datos persistentes, no por su nombre visible.
    public void onToolUse(final PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!this.snapshots.containsKey(this.key(player))) return;
        String id = this.toolId(event.getItem());
        if (id == null) return;
        event.setCancelled(true);
        if (id.equals("vanish")) {
            this.setVanish(player, !this.isVanished(player));
            this.plugin.messages().send(player, this.isVanished(player) ? "staff.vanish-on" : "staff.vanish-off");
        } else if (id.equals("random")) {
            List<Player> players = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) if (!online.equals(player) && !this.isVanished(online)) players.add(online);
            if (!players.isEmpty()) player.teleport(players.get((int) (Math.random() * players.size())).getLocation());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInspect(final PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player target) || !this.snapshots.containsKey(this.key(event.getPlayer()))) return;
        String id = this.toolId(event.getPlayer().getInventory().getItemInMainHand());
        if (id == null) return;
        if (id.equals("inventory")) {
            event.setCancelled(true);
            event.getPlayer().openInventory(target.getInventory());
        } else if (id.equals("ender")) {
            event.setCancelled(true);
            event.getPlayer().openInventory(target.getEnderChest());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInspectClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !this.snapshots.containsKey(this.key(player))) return;
        if (this.configFile.yaml().getBoolean("staffmode.inspection-read-only", true)
            && event.getView().getTopInventory().getHolder() instanceof Player) event.setCancelled(true);
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(final AsyncPlayerChatEvent event) {
        if (this.staffChatEnabled.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(this.plugin, () -> this.sendStaffMessage(event.getPlayer(), event.getMessage()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMobTarget(final EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player player && this.isVanished(player)) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(final EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && this.isVanished(player)
            && this.configFile.yaml().getBoolean("vanish.disable-item-pickup", true)) event.setCancelled(true);
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.handleJoin(event.getPlayer()), 5L);
    }

    private void handleJoin(final Player player) {
        if (!player.isOnline() || !this.plugin.moduleEnabled("staff")) return;
        this.refreshVisibility();
        StaffSnapshot snapshot = this.snapshots.get(this.key(player));
        if (snapshot != null) {
            this.snapshots.remove(this.key(player));
            snapshot.restore(player);
            this.setVanish(player, snapshot.vanishedBefore());
            this.saveData();
            this.plugin.messages().send(player, "staff.staffmode-recovered");
        }
        if (this.isVanished(player)) this.setVanish(player, true);
    }

    private String key(final Player player) { return player.getName().toLowerCase(Locale.ROOT); }

    private Player findOnline(final String name) {
        for (Player player : Bukkit.getOnlinePlayers()) if (player.getName().equalsIgnoreCase(name)) return player;
        return null;
    }

    // Los estados pendientes permiten recuperar inventarios incluso después de un reinicio.
    private void loadData() {
        if (!this.dataFile.exists()) return;
        YamlConfiguration yaml = this.plugin.dataStore().load(this.dataFile);
        this.vanished.addAll(yaml.getStringList("vanished"));
        ConfigurationSection section = yaml.getConfigurationSection("staffmode");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            StaffSnapshot snapshot = StaffSnapshot.load(yaml, "staffmode." + key);
            if (snapshot != null) this.snapshots.put(key.toLowerCase(Locale.ROOT), snapshot);
        }
    }

    private void saveData() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("vanished", new ArrayList<>(this.vanished));
        for (Map.Entry<String, StaffSnapshot> entry : this.snapshots.entrySet()) entry.getValue().save(yaml, "staffmode." + entry.getKey());
        this.plugin.dataStore().saveAsync(this.dataFile, yaml);
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args) {
        if (command.getName().equalsIgnoreCase("staffchat") && args.length == 1
            && "toggle".startsWith(args[0].toLowerCase(Locale.ROOT))) {
            return Collections.singletonList("toggle");
        }
        if (args.length != 1 || (!command.getName().equalsIgnoreCase("vanish") && !command.getName().equalsIgnoreCase("staffmode"))) return Collections.emptyList();
        List<String> names = new ArrayList<>();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        for (Player player : Bukkit.getOnlinePlayers()) if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) names.add(player.getName());
        Collections.sort(names);
        return names;
    }

}