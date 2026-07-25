package me.jaime.emsichill.staff;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

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

    public OpenResult open(final Player viewer, final Player target, final Type type) {
        if (!viewer.hasPermission(type.viewPermission)) return OpenResult.NO_PERMISSION;
        Inventory inventory = type == Type.INVENTORY ? this.playerInventoryView(target) : target.getEnderChest();
        InventoryView view = viewer.openInventory(inventory);
        boolean editable = viewer.hasPermission(type.modifyPermission);
        this.sessions.put(viewer.getUniqueId(),
            new InspectionSession(view.getTopInventory(), !editable, type, target.getUniqueId()));
        return editable ? OpenResult.OPENED_EDITABLE : OpenResult.OPENED_READ_ONLY;
    }

    public boolean shouldCancelClick(
        final Player viewer,
        final InventoryView view,
        final int rawSlot,
        final boolean shiftClick,
        final InventoryAction action
    ) {
        InspectionSession session = this.sessions.get(viewer.getUniqueId());
        if (session == null || session.inventory() != view.getTopInventory()) return false;
        if (session.readOnly()) return true;
        if (session.type() != Type.INVENTORY) return false;
        if (shiftClick || action == InventoryAction.COLLECT_TO_CURSOR) return true;
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
        Inventory inventory = Bukkit.createInventory(null, VIEW_SIZE, Component.text("Inventario de " + target.getName()));
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
        return inventory;
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

    private static ItemStack cloneItem(final ItemStack item) {
        return item == null || item.isEmpty() ? null : item.clone();
    }

    private record InspectionSession(Inventory inventory, boolean readOnly, Type type, UUID targetId) {
    }
}
