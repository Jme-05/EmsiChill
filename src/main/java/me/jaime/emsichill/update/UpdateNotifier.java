package me.jaime.emsichill.update;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

import me.jaime.emsichill.Main;

/** Programa comprobaciones automáticas y entrega cada aviso una sola vez por Release. */
public final class UpdateNotifier implements Listener {
    private static final long STARTUP_DELAY_TICKS = 100L;
    private static final long JOIN_DELAY_TICKS = 40L;
    private static final long TICKS_PER_MINUTE = 20L * 60L;

    private final Main plugin;
    private final UpdateService updates;
    private final UpdateNoticeTracker notices = new UpdateNoticeTracker();

    private volatile UpdateResult cachedResult;
    private volatile boolean checking;
    private boolean failureLogged;
    private BukkitTask task;

    public UpdateNotifier(final Main plugin, final UpdateService updates) {
        this.plugin = plugin;
        this.updates = updates;
    }

    public void start() {
        this.stop();
        if (!this.automaticChecksEnabled()) return;

        long period = this.checkIntervalMinutes() * TICKS_PER_MINUTE;
        this.task = Bukkit.getScheduler().runTaskTimer(
            this.plugin,
            this::requestCheck,
            STARTUP_DELAY_TICKS,
            period
        );
    }

    public void stop() {
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }
    }

    public void reloadConfiguration() {
        this.start();
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        if (!this.automaticChecksEnabled() || !this.notifyAdmins()) return;
        Player player = event.getPlayer();
        if (!player.hasPermission("emsichill.admin.update")) return;

        Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.notifyPlayer(player), JOIN_DELAY_TICKS);
    }

    private void requestCheck() {
        if (this.checking || !this.automaticChecksEnabled()) return;
        this.checking = true;
        this.updates.check().whenComplete((result, failure) -> Bukkit.getScheduler().runTask(this.plugin, () -> {
            this.checking = false;
            if (!this.automaticChecksEnabled()) return;
            if (failure != null) {
                this.logFailure(failure.getMessage());
                return;
            }
            this.cachedResult = result;
            if (result.status() == UpdateResult.Status.FAILED) {
                this.logFailure(result.error());
                return;
            }
            this.failureLogged = false;
            if (result.status() != UpdateResult.Status.UPDATE_AVAILABLE) return;
            this.notifyConsole(result);
            if (this.notifyAdmins()) {
                Bukkit.getOnlinePlayers().forEach(this::notifyPlayer);
            }
        }));
    }

    private void notifyConsole(final UpdateResult result) {
        if (!this.plugin.settings().getBoolean("updates.automatic.notify-console", true)) return;
        if (!this.notices.markConsole(result.release().tag())) return;
        this.plugin.messages().sendLink(Bukkit.getConsoleSender(), "update.automatic-available",
            result.release().pageUrl(),
            "{current}", result.currentVersion(),
            "{latest}", result.release().tag());
    }

    private void notifyPlayer(final Player player) {
        if (!this.automaticChecksEnabled() || !this.notifyAdmins()) return;
        if (!player.isOnline() || !player.hasPermission("emsichill.admin.update")) return;
        UpdateResult result = this.cachedResult;
        if (result == null || result.status() != UpdateResult.Status.UPDATE_AVAILABLE) return;
        if (!this.notices.markPlayer(result.release().tag(), player.getUniqueId())) return;
        this.plugin.messages().sendLink(player, "update.automatic-available", result.release().pageUrl(),
            "{current}", result.currentVersion(),
            "{latest}", result.release().tag());
    }

    private void logFailure(final String detail) {
        if (this.failureLogged) return;
        this.failureLogged = true;
        this.plugin.getLogger().warning("No se pudo comprobar automáticamente la actualización: " + detail);
    }

    private boolean automaticChecksEnabled() {
        return this.plugin.settings().getBoolean("updates.enabled", true)
            && this.plugin.settings().getBoolean("updates.automatic.enabled", true);
    }

    private boolean notifyAdmins() {
        return this.plugin.settings().getBoolean("updates.automatic.notify-admins", true);
    }

    private long checkIntervalMinutes() {
        return Math.max(5L, Math.min(1440L,
            this.plugin.settings().getLong("updates.automatic.interval-minutes", 30L)));
    }
}
