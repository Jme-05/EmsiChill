package me.jaime.emsichill.update;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

import me.jaime.emsichill.Main;

/** Schedules update checks and sends each notice only once per target. */
public final class UpdateNotifier implements Listener {
    private static final long STARTUP_DELAY_TICKS = 100L;
    private static final long JOIN_DELAY_TICKS = 40L;
    private static final long TICKS_PER_MINUTE = 20L * 60L;

    private final Main plugin;
    private final UpdateService updates;
    private final UpdateNoticeTracker notices = new UpdateNoticeTracker();
    private final UpdateNoticeTracker paperNotices = new UpdateNoticeTracker();

    private volatile UpdateResult cachedResult;
    private volatile PaperUpdateResult cachedPaperResult;
    private volatile long cachedAtMillis;
    private volatile long cachedPaperAtMillis;
    private volatile boolean checking;
    private volatile boolean checkingPaper;
    private boolean failureLogged;
    private boolean paperFailureLogged;
    private BukkitTask task;

    public UpdateNotifier(final Main plugin, final UpdateService updates) {
        this.plugin = plugin;
        this.updates = updates;
    }

    public void start() {
        this.stop();
        if (!this.automaticChecksEnabled() && !this.paperAutomaticChecksEnabled()) return;

        long period = this.checkIntervalMinutes() * TICKS_PER_MINUTE;
        this.task = Bukkit.getScheduler().runTaskTimer(
            this.plugin,
            () -> {
                this.requestCheck();
                this.requestPaperCheck();
            },
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
        if ((!this.automaticChecksEnabled() && !this.paperAutomaticChecksEnabled())
            || (!this.notifyAdmins() && !this.paperNotifyAdmins())) return;
        Player player = event.getPlayer();
        if (!player.hasPermission("emsichill.admin.update")) return;

        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            this.notifyPlayer(player);
            this.notifyPaperPlayer(player);
            if (this.checkOnAdminJoin() && shouldCheckOnJoin(this.cachedAtMillis,
                System.currentTimeMillis(), this.joinCacheMaxAgeMillis())) {
                this.requestCheck();
            }
            if (this.paperCheckOnAdminJoin() && shouldCheckOnJoin(this.cachedPaperAtMillis,
                System.currentTimeMillis(), this.paperJoinCacheMaxAgeMillis())) {
                this.requestPaperCheck();
            }
        }, JOIN_DELAY_TICKS);
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
            this.cachedAtMillis = System.currentTimeMillis();
            this.failureLogged = false;
            if (result.status() != UpdateResult.Status.UPDATE_AVAILABLE) return;
            if (this.updates.isSuppressed(result.release().tag())) return;
            this.notifyConsole(result);
            if (this.notifyAdmins()) {
                Bukkit.getOnlinePlayers().forEach(this::notifyPlayer);
            }
        }));
    }

    private void requestPaperCheck() {
        if (this.checkingPaper || !this.paperAutomaticChecksEnabled()) return;
        this.checkingPaper = true;
        this.updates.checkPaper().whenComplete((result, failure) -> Bukkit.getScheduler().runTask(this.plugin, () -> {
            this.checkingPaper = false;
            if (!this.paperAutomaticChecksEnabled()) return;
            if (failure != null) {
                this.logPaperFailure(failure.getMessage());
                return;
            }
            this.cachedPaperResult = result;
            if (result.status() == PaperUpdateResult.Status.FAILED) {
                this.logPaperFailure(result.error());
                return;
            }
            this.cachedPaperAtMillis = System.currentTimeMillis();
            this.paperFailureLogged = false;
            if (result.status() != PaperUpdateResult.Status.UPDATE_AVAILABLE) return;
            if (this.updates.isPaperSuppressed(result.latest())) return;
            this.notifyPaperConsole(result);
            if (this.paperNotifyAdmins()) {
                Bukkit.getOnlinePlayers().forEach(this::notifyPaperPlayer);
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
        this.plugin.messages().sendUpdateActions(player, result.release().tag());
    }

    private void notifyPaperConsole(final PaperUpdateResult result) {
        if (!this.plugin.settings().getBoolean("updates.paper.automatic.notify-console", true)) return;
        if (!this.paperNotices.markConsole(result.latest().identifier())) return;
        this.plugin.messages().sendLink(Bukkit.getConsoleSender(), "update.paper-automatic-available",
            result.latest().pageUrl(),
            "{current}", result.current().label(),
            "{version}", result.latest().minecraftVersion(),
            "{build}", Integer.toString(result.latest().build()),
            "{channel}", result.latest().channel());
    }

    private void notifyPaperPlayer(final Player player) {
        if (!this.paperAutomaticChecksEnabled() || !this.paperNotifyAdmins()) return;
        if (!player.isOnline() || !player.hasPermission("emsichill.admin.update")) return;
        PaperUpdateResult result = this.cachedPaperResult;
        if (result == null || result.status() != PaperUpdateResult.Status.UPDATE_AVAILABLE) return;
        if (!this.paperNotices.markPlayer(result.latest().identifier(), player.getUniqueId())) return;
        this.plugin.messages().sendLink(player, "update.paper-automatic-available", result.latest().pageUrl(),
            "{current}", result.current().label(),
            "{version}", result.latest().minecraftVersion(),
            "{build}", Integer.toString(result.latest().build()),
            "{channel}", result.latest().channel());
        this.plugin.messages().sendPaperUpdateActions(player, result.latest().minecraftVersion(), result.latest().build());
    }

    private void logFailure(final String detail) {
        if (this.failureLogged) return;
        this.failureLogged = true;
        this.plugin.getLogger().warning("Could not check for updates automatically: " + detail);
    }

    private void logPaperFailure(final String detail) {
        if (this.paperFailureLogged) return;
        this.paperFailureLogged = true;
        this.plugin.getLogger().warning("Could not check PaperMC automatically: " + detail);
    }

    private boolean automaticChecksEnabled() {
        return this.plugin.settings().getBoolean("updates.enabled", true)
            && this.plugin.settings().getBoolean("updates.automatic.enabled", true);
    }

    private boolean paperAutomaticChecksEnabled() {
        return this.plugin.settings().getBoolean("updates.paper.enabled", true)
            && this.plugin.settings().getBoolean("updates.paper.automatic.enabled", true);
    }

    private boolean notifyAdmins() {
        return this.plugin.settings().getBoolean("updates.automatic.notify-admins", true);
    }

    private boolean paperNotifyAdmins() {
        return this.plugin.settings().getBoolean("updates.paper.automatic.notify-admins", true);
    }

    private boolean checkOnAdminJoin() {
        return this.plugin.settings().getBoolean("updates.automatic.check-on-admin-join", true);
    }

    private boolean paperCheckOnAdminJoin() {
        return this.plugin.settings().getBoolean("updates.paper.automatic.check-on-admin-join", true);
    }

    private long joinCacheMaxAgeMillis() {
        long seconds = Math.max(15L, Math.min(3_600L,
            this.plugin.settings().getLong("updates.automatic.join-cache-max-age-seconds", 60L)));
        return seconds * 1_000L;
    }

    private long paperJoinCacheMaxAgeMillis() {
        long seconds = Math.max(15L, Math.min(3_600L,
            this.plugin.settings().getLong("updates.paper.automatic.join-cache-max-age-seconds", 60L)));
        return seconds * 1_000L;
    }

    static boolean shouldCheckOnJoin(final long cachedAt, final long now, final long maxAge) {
        return cachedAt <= 0L || now < cachedAt || now - cachedAt >= maxAge;
    }

    private long checkIntervalMinutes() {
        long pluginInterval = Math.max(5L, Math.min(1440L,
            this.plugin.settings().getLong("updates.automatic.interval-minutes", 30L)));
        long paperInterval = Math.max(5L, Math.min(1440L,
            this.plugin.settings().getLong("updates.paper.automatic.interval-minutes", pluginInterval)));
        if (!this.automaticChecksEnabled()) return paperInterval;
        if (!this.paperAutomaticChecksEnabled()) return pluginInterval;
        return Math.min(pluginInterval, paperInterval);
    }
}
