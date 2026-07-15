package me.jaime.emsichill.skin;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Construye y procesa el inventario gráfico de skins favoritas. */
public final class SkinMenu {
    public enum Type {
        FAVORITES,
        HISTORY
    }

    public record State(Type type, int page, List<String> entries) {
        public String skinAt(final int rawSlot) {
            int index = this.page * 45 + rawSlot;
            return rawSlot < 0 || rawSlot >= 45 || index >= this.entries.size()
                ? null
                : this.entries.get(index);
        }
    }

    private static final DateTimeFormatter HISTORY_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        .withZone(ZoneId.systemDefault());

    private final SkinRepository repository;

    public SkinMenu(final SkinRepository repository) {
        this.repository = repository;
    }

    public void open(final Player player, final Type type, final int requestedPage) {
        List<SkinHistoryEntry> history = this.repository.history(player.getName());
        List<String> entries = type == Type.FAVORITES
            ? this.repository.favorites(player.getName())
            : history.stream().map(SkinHistoryEntry::skin).toList();

        int maxPage = Math.max(0, (entries.size() - 1) / 45);
        int page = Math.clamp(requestedPage, 0, maxPage);
        MenuHolder holder = new MenuHolder(player.getUniqueId(), type, page, entries);
        String title = type == Type.FAVORITES ? "Skins favoritas" : "Historial de skins";
        Inventory inventory = Bukkit.createInventory(holder, 54, Component.text(title + " - " + (page + 1)));
        holder.inventory = inventory;

        int start = page * 45;
        for (int slot = 0; slot < 45 && start + slot < entries.size(); slot++) {
            ItemStack item = type == Type.HISTORY
                ? this.historyHead(history.get(start + slot))
                : this.skinHead(entries.get(start + slot));
            inventory.setItem(slot, item);
        }
        if (page > 0) {
            inventory.setItem(45, item(Material.ARROW, "Página anterior", NamedTextColor.YELLOW));
        }
        inventory.setItem(49, item(Material.BARRIER, "Cerrar", NamedTextColor.RED));
        if (page < maxPage) {
            inventory.setItem(53, item(Material.ARROW, "Página siguiente", NamedTextColor.YELLOW));
        }
        player.openInventory(inventory);
    }

    public State state(final InventoryClickEvent event, final Player player) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof MenuHolder menu) || !menu.playerId.equals(player.getUniqueId())) {
            return null;
        }
        return new State(menu.type, menu.page, menu.entries);
    }

    private ItemStack skinHead(final String skinName) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.displayName(Component.text(skinName, NamedTextColor.YELLOW));
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(skinName));
        meta.lore(List.of(
            Component.text("Clic: aplicar la skin.", NamedTextColor.GRAY),
            Component.text("Clic derecho: eliminar de favoritos.", NamedTextColor.DARK_GRAY)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack historyHead(final SkinHistoryEntry history) {
        ItemStack item = this.skinHead(history.skin());
        ItemMeta meta = item.getItemMeta();
        meta.lore(List.of(
            Component.text("Fecha: " + HISTORY_DATE.format(Instant.ofEpochMilli(history.timestamp())), NamedTextColor.GRAY),
            Component.text("Aplicada por: " + history.actor(), NamedTextColor.GRAY),
            Component.text("Origen: " + displaySource(history.source()), NamedTextColor.DARK_GRAY),
            Component.text("Clic: aplicar la skin.", NamedTextColor.YELLOW)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack item(final Material material, final String name, final NamedTextColor color) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color));
        item.setItemMeta(meta);
        return item;
    }

    private static String displaySource(final String source) {
        return switch (source.toLowerCase(Locale.ROOT)) {
            case "admin" -> "administración";
            case "admin-random" -> "aleatoria por administración";
            case "command" -> "comando";
            case "random" -> "aleatoria";
            default -> "desconocido";
        };
    }

    private static final class MenuHolder implements InventoryHolder {
        private final UUID playerId;
        private final Type type;
        private final int page;
        private final List<String> entries;
        private Inventory inventory;

        private MenuHolder(final UUID playerId, final Type type, final int page, final List<String> entries) {
            this.playerId = playerId;
            this.type = type;
            this.page = page;
            this.entries = new ArrayList<>(entries);
        }

        @Override
        public Inventory getInventory() {
            return this.inventory;
        }
    }
}