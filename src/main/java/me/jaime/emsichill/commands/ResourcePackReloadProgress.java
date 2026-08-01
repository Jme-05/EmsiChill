package me.jaime.emsichill.commands;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import me.jaime.emsichill.Main;
import me.jaime.emsichill.config.Messages;
import me.jaime.emsichill.resourcepack.ResourcePackManager;
import net.kyori.adventure.bossbar.BossBar;

/** Displays compact, localized progress while resource packs are prepared. */
final class ResourcePackReloadProgress {
    private static final long HIDE_DELAY_TICKS = 50L;

    private final Main plugin;
    private final Messages messages;
    private final Player player;
    private BossBar bar;
    private BukkitTask animation;
    private String messageKey = "resource-pack.progress-preparing";
    private float ceiling = 0.16F;
    private boolean finished;

    ResourcePackReloadProgress(final Main plugin, final Player player) {
        this.plugin = plugin;
        this.messages = plugin.messages();
        this.player = player;
    }

    void start() {
        this.runSync(() -> {
            if (this.finished || !this.player.isOnline() || this.bar != null) return;
            this.bar = BossBar.bossBar(
                this.title(0.03F),
                0.03F,
                BossBar.Color.BLUE,
                BossBar.Overlay.PROGRESS
            );
            this.player.showBossBar(this.bar);
            this.animation = Bukkit.getScheduler().runTaskTimer(this.plugin, this::animate, 5L, 5L);
        });
    }

    void phase(final ResourcePackManager.ReloadPhase phase) {
        this.runSync(() -> {
            if (this.finished || this.bar == null) return;
            switch (phase) {
                case PREPARING -> this.advance("resource-pack.progress-preparing", 0.03F, 0.16F);
                case BUILDING -> this.advance("resource-pack.progress-building", 0.18F, 0.48F);
                case HOSTING -> this.advance("resource-pack.progress-hosting", 0.50F, 0.90F);
                case ACTIVATING -> this.advance("resource-pack.progress-activating", 0.92F, 0.98F);
            }
        });
    }

    void complete() {
        this.finish("resource-pack.progress-complete", BossBar.Color.GREEN, 1.0F);
    }

    void empty() {
        this.finish("resource-pack.progress-empty", BossBar.Color.YELLOW, 1.0F);
    }

    void busy() {
        this.finish("resource-pack.progress-busy", BossBar.Color.YELLOW, 1.0F);
    }

    void fail() {
        this.finish("resource-pack.progress-failed", BossBar.Color.RED, null);
    }

    private void advance(final String key, final float minimum, final float newCeiling) {
        this.messageKey = key;
        this.ceiling = newCeiling;
        if (this.bar.progress() < minimum) this.bar.progress(minimum);
        this.bar.name(this.title(this.bar.progress()));
    }

    private void animate() {
        if (this.finished || this.bar == null || !this.player.isOnline()) {
            this.hide();
            return;
        }
        float current = this.bar.progress();
        float step = Math.max(0.003F, (this.ceiling - current) * 0.08F);
        float next = Math.min(this.ceiling, current + step);
        this.bar.progress(next);
        this.bar.name(this.title(next));
    }

    private void finish(final String key, final BossBar.Color color, final Float progress) {
        this.runSync(() -> {
            if (this.finished) return;
            this.finished = true;
            if (this.animation != null) this.animation.cancel();
            if (this.bar == null || !this.player.isOnline()) {
                this.hide();
                return;
            }
            if (progress != null) this.bar.progress(progress);
            this.bar.color(color);
            this.bar.name(this.messages.unprefixed(this.player, key));
            Bukkit.getScheduler().runTaskLater(this.plugin, this::hide, HIDE_DELAY_TICKS);
        });
    }

    private net.kyori.adventure.text.Component title(final float progress) {
        return this.messages.unprefixed(this.player, this.messageKey,
            "{percent}", Integer.toString(Math.round(progress * 100.0F)));
    }

    private void hide() {
        if (this.animation != null) {
            this.animation.cancel();
            this.animation = null;
        }
        if (this.bar != null) {
            this.player.hideBossBar(this.bar);
            this.bar = null;
        }
    }

    private void runSync(final Runnable action) {
        if (!this.plugin.isEnabled()) return;
        if (Bukkit.isPrimaryThread()) action.run();
        else Bukkit.getScheduler().runTask(this.plugin, action);
    }
}
