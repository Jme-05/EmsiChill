package me.jaime.emsichill.staff;

import java.util.ArrayList;
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
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import me.jaime.emsichill.Main;
import me.jaime.emsichill.config.ConfigFile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Reglas de staffchat, vanish y Staff Mode, sin analizar comandos ni escuchar eventos. */
public final class StaffService {
    private final Main plugin;
    private final StaffRepository repository;
    private final NamespacedKey toolKey;
    private final Set<String> vanished = ConcurrentHashMap.newKeySet();
    private final Set<UUID> staffChatEnabled = ConcurrentHashMap.newKeySet();
    private final Map<String, StaffSnapshot> snapshots = new ConcurrentHashMap<>();
    private ConfigFile configFile;

    public StaffService(final Main plugin) {
        this.plugin = plugin;
        this.repository = new StaffRepository(plugin);
        this.toolKey = new NamespacedKey(plugin, "staff_tool");
        StaffData data = this.repository.load();
        this.vanished.addAll(data.vanished());
        this.snapshots.putAll(data.snapshots());
        this.reloadConfiguration();
    }

    public void reloadConfiguration() {
        if (this.configFile == null) this.configFile = new ConfigFile(this.plugin, "Staff/config.yml");
        else this.configFile.reload();
        if (this.configFile.yaml().contains("staffmode.inspection-read-only")) {
            this.configFile.yaml().set("staffmode.inspection-read-only", null);
            this.configFile.save();
        }
        this.refreshVisibility();
    }

    public void start() {
        for (Player player : Bukkit.getOnlinePlayers()) this.recoverPlayer(player);
    }

    public void stop() {
        for (Player player : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            if (this.isStaffMode(player)) this.disableStaffMode(player);
        }
        this.saveData();
    }

    public int vanishedCount() { return this.vanished.size(); }
    public void persistData() { this.saveData(); }
    public boolean showStaffChatInConsole() {
        return this.configFile.yaml().getBoolean("staffchat.show-in-console", true);
    }

    public boolean toggleStaffChat(final Player player) {
        if (this.staffChatEnabled.remove(player.getUniqueId())) return false;
        this.staffChatEnabled.add(player.getUniqueId());
        return true;
    }

    public boolean usesStaffChat(final Player player) {
        return this.staffChatEnabled.contains(player.getUniqueId());
    }

    public void leave(final Player player) {
        this.staffChatEnabled.remove(player.getUniqueId());
    }

    public void sendStaffMessage(final org.bukkit.command.CommandSender sender, final String text) {
        Component message = Component.text("[Staff] ", NamedTextColor.AQUA)
            .append(Component.text(sender.getName() + ": ", NamedTextColor.YELLOW))
            .append(Component.text(text, NamedTextColor.WHITE));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("emsichill.staffchat")) player.sendMessage(message);
        }
        if (this.showStaffChatInConsole()) Bukkit.getConsoleSender().sendMessage(message);
    }

    public boolean isVanished(final Player player) {
        return this.vanished.contains(this.key(player));
    }

    public boolean toggleVanish(final Player player) {
        boolean enabled = !this.isVanished(player);
        this.setVanish(player, enabled);
        return enabled;
    }

    public void setVanish(final Player target, final boolean enabled) {
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

    public List<String> vanishedNames() {
        return this.vanished.stream().sorted().toList();
    }

    public boolean isStaffMode(final Player player) {
        return this.snapshots.containsKey(this.key(player));
    }

    public boolean toggleStaffMode(final Player player) {
        if (this.isStaffMode(player)) {
            this.disableStaffMode(player);
            return false;
        }
        this.enableStaffMode(player);
        return true;
    }

    public String toolId(final ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(this.toolKey, PersistentDataType.STRING);
    }

    public Player randomVisiblePlayer(final Player viewer) {
        List<Player> candidates = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(viewer) && !this.isVanished(online)) candidates.add(online);
        }
        if (candidates.isEmpty()) return null;
        return candidates.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    public boolean recoverPlayer(final Player player) {
        if (!player.isOnline() || !this.plugin.moduleEnabled("staff")) return false;
        this.refreshVisibility();
        StaffSnapshot snapshot = this.snapshots.remove(this.key(player));
        if (snapshot != null) {
            snapshot.restore(player);
            this.setVanish(player, snapshot.vanishedBefore());
            this.saveData();
            return true;
        }
        if (this.isVanished(player)) this.setVanish(player, true);
        return false;
    }

    public boolean blocksItemPickup(final Player player) {
        return this.isVanished(player) && this.configFile.yaml().getBoolean("vanish.disable-item-pickup", true);
    }

    private void enableStaffMode(final Player player) {
        StaffSnapshot snapshot = StaffSnapshot.capture(player, this.isVanished(player));
        this.snapshots.put(this.key(player), snapshot);
        this.saveData();
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(new ItemStack[4]);
        inventory.setItem(0, this.tool(Material.COMPASS, "random", "staff.tool-random"));
        inventory.setItem(1, this.tool(Material.PLAYER_HEAD, "inventory", "staff.tool-inventory"));
        inventory.setItem(2, this.tool(Material.ENDER_CHEST, "ender", "staff.tool-ender"));
        inventory.setItem(7, this.tool(Material.LIME_DYE, "vanish", "staff.tool-vanish"));
        player.setGameMode(this.staffGameMode());
        if (this.configFile.yaml().getBoolean("staffmode.enable-vanish", true)) this.setVanish(player, true);
    }

    private void disableStaffMode(final Player player) {
        StaffSnapshot snapshot = this.snapshots.remove(this.key(player));
        if (snapshot == null) return;
        snapshot.restore(player);
        if (this.configFile.yaml().getBoolean("staffmode.disable-vanish-on-exit", true)) {
            this.setVanish(player, snapshot.vanishedBefore());
        }
        this.saveData();
    }

    private GameMode staffGameMode() {
        try {
            return GameMode.valueOf(this.configFile.yaml().getString("staffmode.game-mode", "SPECTATOR")
                .toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return GameMode.SPECTATOR;
        }
    }

    private ItemStack tool(final Material material, final String id, final String messageKey) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(this.plugin.messages().unprefixed(messageKey));
        meta.getPersistentDataContainer().set(this.toolKey, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    private String key(final Player player) {
        return player.getName().toLowerCase(Locale.ROOT);
    }

    private void saveData() {
        this.repository.save(this.vanished, this.snapshots);
    }
}
