package me.jaime.emsichill.staff;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Mantiene congelaciones temporales; nunca atrapa a un jugador después de reiniciar. */
public final class FreezeService {
    private final Set<UUID> frozen = ConcurrentHashMap.newKeySet();

    public boolean toggle(final UUID playerId) {
        if (this.frozen.remove(playerId)) return false;
        this.frozen.add(playerId);
        return true;
    }

    public boolean isFrozen(final UUID playerId) {
        return this.frozen.contains(playerId);
    }

    public void release(final UUID playerId) {
        this.frozen.remove(playerId);
    }

    public void clear() {
        this.frozen.clear();
    }
}
