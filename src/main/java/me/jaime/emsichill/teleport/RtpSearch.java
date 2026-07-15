package me.jaime.emsichill.teleport;

import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;

/** Estado cancelable de una búsqueda RTP que puede continuar durante varios callbacks de chunks. */
public final class RtpSearch {
    private final Location origin;
    private final long startedAtMillis;
    private final long delaySeconds;
    private boolean cancelled;
    private BukkitTask task;

    public RtpSearch(final Location origin, final long startedAtMillis, final long delaySeconds) {
        this.origin = origin;
        this.startedAtMillis = startedAtMillis;
        this.delaySeconds = delaySeconds;
    }

    public Location origin() { return this.origin; }
    public long startedAtMillis() { return this.startedAtMillis; }
    public long delaySeconds() { return this.delaySeconds; }
    public boolean cancelled() { return this.cancelled; }
    public void setTask(final BukkitTask task) { this.task = task; }

    public void cancel() {
        this.cancelled = true;
        if (this.task != null) {
            this.task.cancel();
        }
    }
}