package me.jaime.emsichill.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import me.jaime.emsichill.Main;
import me.jaime.emsichill.skin.SkinSettings;
import me.jaime.emsichill.skin.SkinTexture;

/** Entrega cabezas con la textura firmada de una cuenta premium. */
public final class SkullCommand implements CommandExecutor, TabCompleter {
    private static final String PERMISSION = "emsichill.skull";

    private final Main plugin;
    private final SkinCommand skins;

    public SkullCommand(final Main plugin, final SkinCommand skins) {
        this.plugin = plugin;
        this.skins = skins;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label,
                             final String[] args) {
        if (!this.plugin.moduleEnabled("skins")) {
            this.plugin.messages().send(sender, "general.module-disabled");
            return true;
        }
        if (!(sender instanceof Player player)) {
            this.plugin.messages().send(sender, "general.only-players");
            return true;
        }
        if (!player.hasPermission(PERMISSION)) {
            this.plugin.messages().send(player, "general.no-permission");
            return true;
        }
        if (args.length != 1) {
            this.plugin.messages().send(player, "skull.usage");
            return true;
        }

        String requested = args[0];
        if (!SkinSettings.isValidMinecraftName(requested)) {
            this.plugin.messages().send(player, "skull.invalid-name");
            return true;
        }

        this.plugin.messages().send(player, "skull.searching", "{player}", requested);
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> this.lookup(player, requested));
        return true;
    }

    private void lookup(final Player player, final String requested) {
        try {
            SkinTexture texture = this.skins.findTexture(requested);
            Bukkit.getScheduler().runTask(this.plugin, () -> this.finish(player, requested, texture));
        } catch (IOException exception) {
            Bukkit.getScheduler().runTask(this.plugin,
                () -> this.plugin.messages().send(player, "skull.mojang-unavailable"));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private void finish(final Player player, final String requested, final SkinTexture texture) {
        if (!player.isOnline()) return;
        if (texture == null) {
            this.plugin.messages().send(player, "skull.not-found", "{player}", requested);
            return;
        }

        ItemStack head = this.createHead(texture);
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(head);
        overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        this.plugin.messages().send(player, "skull.received", "{player}", texture.name());
        this.plugin.audit().log("SKULL_GIVE", "player=" + player.getName() + " skin=" + texture.name());
    }

    private ItemStack createHead(final SkinTexture texture) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), texture.name());
        profile.setProperty(new ProfileProperty("textures", texture.value(), texture.signature()));
        meta.setPlayerProfile(profile);
        meta.displayName(Component.text("Cabeza de " + texture.name(), NamedTextColor.YELLOW));
        head.setItemMeta(meta);
        return head;
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias,
                                      final String[] args) {
        if (args.length != 1 || !sender.hasPermission(PERMISSION)) return Collections.emptyList();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) matches.add(player.getName());
        }
        matches.sort(String.CASE_INSENSITIVE_ORDER);
        return matches;
    }
}
