package me.jaime.emsichill.social;

import org.bukkit.entity.Player;
/** Conserva la postura de gateo mientras el jugador la utiliza. */
final class PoseController {
    private final CrawlPoseController crawlController;

    PoseController() {
        this.crawlController = new CrawlPoseController();
    }

    boolean apply(final Player player) {
        return this.crawlController.apply(player);
    }

    void clear(final Player player) {
        this.crawlController.clear(player);
    }
}
