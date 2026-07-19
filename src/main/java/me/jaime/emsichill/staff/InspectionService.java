package me.jaime.emsichill.staff;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

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

    public OpenResult open(final Player viewer, final Player target, final Type type) {
        if (!viewer.hasPermission(type.viewPermission)) return OpenResult.NO_PERMISSION;
        Inventory inventory = type == Type.INVENTORY ? target.getInventory() : target.getEnderChest();
        InventoryView view = viewer.openInventory(inventory);
        boolean editable = viewer.hasPermission(type.modifyPermission);
        this.sessions.put(viewer.getUniqueId(), new InspectionSession(view.getTopInventory(), !editable));
        return editable ? OpenResult.OPENED_EDITABLE : OpenResult.OPENED_READ_ONLY;
    }

    public boolean isReadOnly(final Player viewer, final InventoryView view) {
        InspectionSession session = this.sessions.get(viewer.getUniqueId());
        return session != null && session.inventory() == view.getTopInventory() && session.readOnly();
    }

    public void close(final UUID viewerId) {
        this.sessions.remove(viewerId);
    }

    public void clear() {
        this.sessions.clear();
    }

    private record InspectionSession(Inventory inventory, boolean readOnly) {
    }
}
