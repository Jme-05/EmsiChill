package me.jaime.emsichill.skin;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

import me.jaime.emsichill.Main;

/**
 * Aplica texturas al perfil de Paper y coordina la actualización visible del jugador en el
 * servidor una vez completada la consulta asíncrona.
 */
public final class SkinService {
    private final Main plugin;
    private final SkinRepository repository;
    private final SkinProvider provider;
    private final Map<UUID, PlayerProfile> originalProfiles = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    private SkinSettings settings;

    public SkinService(final Main plugin, final SkinRepository repository, final SkinProvider provider,
                       final SkinSettings settings) {
        this.plugin = plugin;
        this.repository = repository;
        this.provider = provider;
        this.settings = settings;
    }

    public void updateSettings(final SkinSettings settings) {
        this.settings = settings;
    }

    public boolean canChangeSkin(final Player player) {
        if (player.hasPermission("emsichill.skin.bypasscooldown") || this.settings.cooldownSeconds() == 0L) {
            return true;
        }
        long elapsed = Instant.now().getEpochSecond() - this.cooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (elapsed >= this.settings.cooldownSeconds()) {
            return true;
        }
        long remaining = this.settings.cooldownSeconds() - elapsed;
        this.plugin.messages().send(player, "skin.cooldown", "{seconds}", Long.toString(remaining));
        return false;
    }

    public void beginCooldown(final Player player) {
        this.cooldowns.put(player.getUniqueId(), Instant.now().getEpochSecond());
    }

    public void applyNamed(final CommandSender sender, final Player target, final String skinName,
                           final boolean changingOther, final boolean announce) {
        this.rememberOriginalProfile(target);
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            SkinTexture texture;
            try {
                texture = this.provider.findByName(skinName);
            } catch (IOException exception) {
                this.runSync(() -> {
                    if (announce) {
                        this.plugin.messages().send(sender, "skin.mojang-unavailable");
                    }
                });
                return;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
            this.runSync(() -> this.finishApply(sender, target, texture, skinName, changingOther, announce, false));
        });
    }

    public void applyRandom(final CommandSender sender, final Player target, final boolean changingOther) {
        this.rememberOriginalProfile(target);
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            SkinTexture texture;
            try {
                texture = this.provider.findRandom();
            } catch (IOException exception) {
                this.runSync(() -> this.plugin.messages().send(sender, "skin.mojang-unavailable"));
                return;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
            this.runSync(() -> this.finishApply(sender, target, texture, "random", changingOther, true, true));
        });
    }

    public void reset(final CommandSender sender, final Player target, final boolean changingOther) {
        PlayerProfile original = this.originalProfiles.remove(target.getUniqueId());
        if (original == null) {
            original = Bukkit.createProfileExact(target.getUniqueId(), target.getName());
        } else {
            original = original.clone();
        }

        target.setPlayerProfile(original);
        this.repository.clearSelection(target.getName());
        this.repository.save();
        this.plugin.messages().send(target, "skin.reset");
        if (changingOther && !sender.equals(target)) {
            this.plugin.messages().send(sender, "skin.reset-other", "{player}", target.getName());
        }
        this.plugin.audit().log("SKIN_RESET", "actor=" + sender.getName() + " player=" + target.getName());
    }

    public void restoreSelectedSkin(final Player player) {
        this.rememberOriginalProfile(player);
        String selected = this.repository.selectedSkin(player.getName());
        if (selected == null) {
            return;
        }

        SkinTexture cached = this.repository.cachedTexture(selected);
        if (cached != null) {
            this.applyTexture(player, cached);
        } else {
            this.applyNamed(Bukkit.getConsoleSender(), player, selected, false, false);
        }
    }

    public void forgetPlayer(final Player player) {
        this.originalProfiles.remove(player.getUniqueId());
        this.cooldowns.remove(player.getUniqueId());
    }

    public void stop() {
        this.originalProfiles.clear();
        this.cooldowns.clear();
    }

    private void finishApply(final CommandSender sender, final Player target, final SkinTexture texture,
                             final String requested, final boolean changingOther, final boolean announce,
                             final boolean random) {
        if (texture == null) {
            if (announce) {
                String message = random ? "skin.no-random-skins" : "skin.not-found";
                this.plugin.messages().send(sender, message, "{skin}", requested);
            }
            return;
        }
        if (!target.isOnline()) {
            if (announce) {
                this.plugin.messages().send(sender, "skin.disconnected");
            }
            return;
        }

        this.applyTexture(target, texture);
        this.repository.selectSkin(target.getName(), texture.name());
        if (announce) {
            String source = random ? (changingOther ? "admin-random" : "random")
                : (changingOther ? "admin" : "command");
            this.repository.addHistory(
                target.getName(),
                new SkinHistoryEntry(texture.name(), System.currentTimeMillis(), sender.getName(), source),
                this.settings
            );
        }
        this.repository.save();

        if (!announce) {
            return;
        }
        this.plugin.messages().send(target, "skin.changed", "{skin}", texture.name());
        if (changingOther && !sender.equals(target)) {
            this.plugin.messages().send(sender, "skin.changed-other", "{player}", target.getName());
        }
        String auditDetails = "actor=" + sender.getName() + " player=" + target.getName()
            + " skin=" + texture.name() + (random ? " source=random" : "");
        this.plugin.audit().log("SKIN_CHANGE", auditDetails);
    }

    private void applyTexture(final Player player, final SkinTexture texture) {
        PlayerProfile profile = player.getPlayerProfile().clone();
        profile.removeProperty("textures");
        profile.setProperty(new ProfileProperty("textures", texture.value(), texture.signature()));
        player.setPlayerProfile(profile);
    }

    private void rememberOriginalProfile(final Player player) {
        this.originalProfiles.putIfAbsent(player.getUniqueId(), player.getPlayerProfile().clone());
    }

    private void runSync(final Runnable action) {
        Bukkit.getScheduler().runTask(this.plugin, action);
    }
}