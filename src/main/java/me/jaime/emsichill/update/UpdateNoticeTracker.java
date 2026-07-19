package me.jaime.emsichill.update;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Evita repetir el aviso de una misma Release a consola y administradores. */
final class UpdateNoticeTracker {
    private final Set<UUID> notifiedPlayers = new HashSet<>();
    private String releaseTag;
    private boolean consoleNotified;

    synchronized boolean markConsole(final String tag) {
        this.selectRelease(tag);
        if (this.consoleNotified) return false;
        this.consoleNotified = true;
        return true;
    }

    synchronized boolean markPlayer(final String tag, final UUID playerId) {
        this.selectRelease(tag);
        return this.notifiedPlayers.add(playerId);
    }

    private void selectRelease(final String tag) {
        if (tag.equals(this.releaseTag)) return;
        this.releaseTag = tag;
        this.consoleNotified = false;
        this.notifiedPlayers.clear();
    }
}
