package me.jaime.emsichill.staff;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;

/** Abre inspecciones y recuerda si el moderador puede modificar su contenido. */
public final class InspectionService {
    public enum Type {
        INVENTORY("emsichill.invsee.view", "emsichill.invsee.modify"),
        ENDER_CHEST("emsichill.enderchestsee.view", "emsichill.enderchestsee.modify");

        private final String viewPermission;
        private final String modifyPermission;

        Type(final String viewPermission, final String modifyPermission) {
            this.viewPermission = viewPermission;
            this.modifyPermission = modifyPermission;
        }
    }

    public enum OpenResult {
        OPENED_READ_ONLY,
        OPENED_EDITABLE,
        NO_PERMISSION
    }

    private final Map<UUID, InspectionSession> sessions = new ConcurrentHashMap<>();
    private static final int VIEW_SIZE = 54;
    private static final int HOTBAR_START = 27;
    private static final int HELMET_SLOT = 45;
    private static final int CHESTPLATE_SLOT = 46;
    private static final int LEGGINGS_SLOT = 47;
    private static final int BOOTS_SLOT = 48;
    private static final int OFF_HAND_SLOT = 50;
    private static final int EQUIPMENT_LABEL_SLOT = 49;

    public OpenResult open(final Player viewer, final Player target, final Type type) {
        if (!viewer.hasPermission(type.viewPermission)) return OpenResult.NO_PERMISSION;
        Inventory inventory = type == Type.INVENTORY ? this.playerInventoryView(target) : target.getEnderChest();
        InventoryView view = viewer.openInventory(inventory);
        boolean selfInventory = type == Type.INVENTORY && viewer.getUniqueId().equals(target.getUniqueId());
        boolean editable = viewer.hasPermission(type.modifyPermission) && !selfInventory;
        this.sessions.put(viewer.getUniqueId(),
            new InspectionSession(view.getTopInventory(), !editable, type, target.getUniqueId()));
        return editable ? OpenResult.OPENED_EDITABLE : OpenResult.OPENED_READ_ONLY;
    }

    public boolean handleClick(final Player viewer, final InventoryClickEvent event) {
        InventoryView view = event.getView();
        InspectionSession session = this.sessions.get(viewer.getUniqueId());
        if (session == null || session.inventory() != view.getTopInventory()) return false;
        if (session.readOnly()) return true;
        if (blockedClick(event.getClick(), event.getAction())) return true;
        if (session.type() == Type.INVENTORY && event.getRawSlot() >= 0
            && event.getRawSlot() < view.getTopInventory().getSize() && !editableInventorySlot(event.getRawSlot())) return true;
        if (!event.isShiftClick()) return false;
        if (event.getAction() != InventoryAction.MOVE_TO_OTHER_INVENTORY) return true;
        if (session.type() == Type.ENDER_CHEST) return false;
        this.moveInventoryShiftClick(viewer, view, event);
        return true;
    }

    public boolean shouldCancelClick(
        final Player viewer,
        final InventoryView view,
        final int rawSlot,
        final ClickType click,
        final boolean shiftClick,
        final InventoryAction action
    ) {
        InspectionSession session = this.sessions.get(viewer.getUniqueId());
        if (session == null || session.inventory() != view.getTopInventory()) return false;
        if (session.readOnly()) return true;
        if (blockedClick(click, action)) return true;
        if (shiftClick) return session.type() == Type.INVENTORY && action != InventoryAction.MOVE_TO_OTHER_INVENTORY;
        if (session.type() != Type.INVENTORY) return false;
        return rawSlot >= 0 && rawSlot < view.getTopInventory().getSize() && !editableInventorySlot(rawSlot);
    }

    public boolean shouldCancelDrag(final Player viewer, final InventoryView view, final Set<Integer> rawSlots) {
        InspectionSession session = this.sessions.get(viewer.getUniqueId());
        if (session == null || session.inventory() != view.getTopInventory()) return false;
        if (session.readOnly()) return true;
        if (session.type() != Type.INVENTORY) return false;
        for (int slot : rawSlots) {
            if (slot >= 0 && slot < view.getTopInventory().getSize() && !editableInventorySlot(slot)) return true;
        }
        return false;
    }

    public void close(final UUID viewerId) {
        InspectionSession session = this.sessions.remove(viewerId);
        if (session == null || session.type() != Type.INVENTORY) return;
        Player target = Bukkit.getPlayer(session.targetId());
        if (target != null) this.syncPlayerInventory(session, target);
    }

    public void closeTarget(final Player target) {
        for (Map.Entry<UUID, InspectionSession> entry : this.sessions.entrySet()) {
            InspectionSession session = entry.getValue();
            if (!session.targetId().equals(target.getUniqueId())) continue;
            if (session.type() == Type.INVENTORY) this.syncPlayerInventory(session, target);
            this.sessions.remove(entry.getKey());
            Player viewer = Bukkit.getPlayer(entry.getKey());
            if (viewer != null) viewer.closeInventory();
        }
    }

    public void clear() {
        this.sessions.clear();
    }

    private Inventory playerInventoryView(final Player target) {
        Inventory inventory = Bukkit.createInventory(null, VIEW_SIZE, Component.text("InvSee: " + target.getName()));
        PlayerInventory source = target.getInventory();
        ItemStack[] storage = source.getStorageContents();
        for (int slot = 9; slot < storage.length; slot++) {
            inventory.setItem(slot - 9, cloneItem(storage[slot]));
        }
        for (int slot = 0; slot < Math.min(9, storage.length); slot++) {
            inventory.setItem(HOTBAR_START + slot, cloneItem(storage[slot]));
        }
        inventory.setItem(HELMET_SLOT, cloneItem(source.getHelmet()));
        inventory.setItem(CHESTPLATE_SLOT, cloneItem(source.getChestplate()));
        inventory.setItem(LEGGINGS_SLOT, cloneItem(source.getLeggings()));
        inventory.setItem(BOOTS_SLOT, cloneItem(source.getBoots()));
        inventory.setItem(OFF_HAND_SLOT, cloneItem(source.getItemInOffHand()));
        this.decorateInventoryView(inventory);
        return inventory;
    }

    private void decorateInventoryView(final Inventory inventory) {
        for (int slot = 36; slot <= 44; slot++) inventory.setItem(slot, separator());
        inventory.setItem(EQUIPMENT_LABEL_SLOT, separator());
        inventory.setItem(51, separator());
        inventory.setItem(52, separator());
        inventory.setItem(53, separator());
    }

    private void syncPlayerInventory(final InspectionSession session, final Player target) {
        Inventory source = session.inventory();
        PlayerInventory destination = target.getInventory();
        ItemStack[] storage = destination.getStorageContents();
        for (int slot = 9; slot < storage.length; slot++) {
            storage[slot] = cloneItem(source.getItem(slot - 9));
        }
        for (int slot = 0; slot < Math.min(9, storage.length); slot++) {
            storage[slot] = cloneItem(source.getItem(HOTBAR_START + slot));
        }
        destination.setStorageContents(storage);
        destination.setHelmet(cloneItem(source.getItem(HELMET_SLOT)));
        destination.setChestplate(cloneItem(source.getItem(CHESTPLATE_SLOT)));
        destination.setLeggings(cloneItem(source.getItem(LEGGINGS_SLOT)));
        destination.setBoots(cloneItem(source.getItem(BOOTS_SLOT)));
        destination.setItemInOffHand(cloneItem(source.getItem(OFF_HAND_SLOT)));
        target.updateInventory();
    }

    private static boolean editableInventorySlot(final int slot) {
        return (slot >= 0 && slot < 36) || slot == HELMET_SLOT || slot == CHESTPLATE_SLOT
            || slot == LEGGINGS_SLOT || slot == BOOTS_SLOT || slot == OFF_HAND_SLOT;
    }

    private void moveInventoryShiftClick(final Player viewer, final InventoryView view, final InventoryClickEvent event) {
        ItemStack clicked = cloneItem(event.getCurrentItem());
        if (clicked == null) return;
        Inventory top = view.getTopInventory();
        if (event.getRawSlot() >= 0 && event.getRawSlot() < top.getSize()) {
            if (!editableInventorySlot(event.getRawSlot())) return;
            ItemStack leftover = this.addToPlayerStorage(view.getBottomInventory(), clicked);
            top.setItem(event.getRawSlot(), leftover);
        } else if (event.getClickedInventory() != null && event.getClickedInventory().equals(view.getBottomInventory())) {
            ItemStack leftover = this.addToEditableInspectionSlots(top, clicked);
            event.getClickedInventory().setItem(event.getSlot(), leftover);
        }
        viewer.updateInventory();
    }

    private ItemStack addToEditableInspectionSlots(final Inventory inventory, final ItemStack source) {
        ItemStack remaining = cloneItem(source);
        for (int slot = 0; slot < 36 && remaining != null; slot++) {
            remaining = this.mergeIntoSlot(inventory, slot, remaining);
        }
        return remaining;
    }

    private ItemStack addToPlayerStorage(final Inventory inventory, final ItemStack source) {
        ItemStack remaining = cloneItem(source);
        int size = inventory instanceof PlayerInventory ? Math.min(36, inventory.getSize()) : inventory.getSize();
        for (int slot = 0; slot < size && remaining != null; slot++) {
            remaining = this.mergeIntoSlot(inventory, slot, remaining);
        }
        return remaining;
    }

    private ItemStack mergeIntoSlot(final Inventory inventory, final int slot, final ItemStack remaining) {
        ItemStack current = inventory.getItem(slot);
        if (current == null || current.isEmpty()) {
            inventory.setItem(slot, remaining);
            return null;
        }
        if (!current.isSimilar(remaining) || current.getAmount() >= current.getMaxStackSize()) return remaining;
        int moved = Math.min(remaining.getAmount(), current.getMaxStackSize() - current.getAmount());
        current.setAmount(current.getAmount() + moved);
        remaining.setAmount(remaining.getAmount() - moved);
        return remaining.getAmount() <= 0 ? null : remaining;
    }

    private static boolean blockedClick(final ClickType click, final InventoryAction action) {
        if (click == ClickType.NUMBER_KEY || click == ClickType.SWAP_OFFHAND
            || click == ClickType.DROP || click == ClickType.CONTROL_DROP || click == ClickType.CREATIVE) return true;
        return switch (action) {
            case HOTBAR_MOVE_AND_READD, HOTBAR_SWAP, COLLECT_TO_CURSOR, DROP_ALL_CURSOR, DROP_ONE_CURSOR,
                DROP_ALL_SLOT, DROP_ONE_SLOT, CLONE_STACK, UNKNOWN -> true;
            default -> false;
        };
    }

    private static ItemStack cloneItem(final ItemStack item) {
        return item == null || item.isEmpty() ? null : item.clone();
    }

    private static ItemStack separator() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" "));
        item.setItemMeta(meta);
        return item;
    }

    private record InspectionSession(Inventory inventory, boolean readOnly, Type type, UUID targetId) {
    }
}
