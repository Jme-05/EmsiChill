package me.jaime.emsichill.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import me.jaime.emsichill.Main;
import me.jaime.emsichill.config.ConfigFile;
import me.jaime.emsichill.skin.SkinMenu;
import me.jaime.emsichill.skin.SkinProvider;
import me.jaime.emsichill.skin.SkinRepository;
import me.jaime.emsichill.skin.SkinRepository.FavoriteResult;
import me.jaime.emsichill.skin.SkinService;
import me.jaime.emsichill.skin.SkinSettings;
import me.jaime.emsichill.skin.SkinTexture;

/**
 * Interpreta /skin y conecta las acciones del jugador con el proveedor, repositorio, servicio de
 * aplicación y menú de skins.
 */
public final class SkinCommand implements CommandExecutor, TabCompleter, Listener {
    private static final String SELF_PERMISSION = "emsichill.skin";
    private static final String OTHERS_PERMISSION = "emsichill.skin.others";
    private static final List<String> SUBCOMMANDS = List.of(
        "random", "reset", "save", "unsave", "favorites", "history", "clearhistory"
    );

    private final Main plugin;
    private final SkinRepository repository;
    private final SkinProvider provider;
    private final SkinService service;
    private final SkinMenu menu;

    private ConfigFile configFile;
    private SkinSettings settings;

    public SkinCommand(final Main plugin) {
        this.plugin = plugin;
        this.configFile = new ConfigFile(plugin, "Skin/config.yml");
        this.settings = SkinSettings.from(this.configFile.yaml());
        this.repository = new SkinRepository(plugin);
        this.provider = new SkinProvider(this.repository, this.settings);
        this.service = new SkinService(plugin, this.repository, this.provider, this.settings);
        this.menu = new SkinMenu(this.repository);
    }

    public void reloadConfiguration() {
        this.configFile.reload();
        this.settings = SkinSettings.from(this.configFile.yaml());
        this.provider.updateSettings(this.settings);
        this.service.updateSettings(this.settings);
    }

    public void start() {
        if (!this.enabled()) {
            return;
        }
        Bukkit.getOnlinePlayers().forEach(this.service::restoreSelectedSkin);
    }

    public void stop() {
        this.repository.save();
        this.service.stop();
    }

    public int selectedSkinCount() {
        return this.repository.selectedSkinCount();
    }

    public int cachedTextureCount() {
        return this.repository.cachedTextureCount();
    }

    /** Comparte la búsqueda y la caché de texturas con comandos relacionados. */
    public SkinTexture findTexture(final String skinName) throws IOException, InterruptedException {
        return this.provider.findByName(skinName);
    }

    public void persistData() {
        this.repository.save();
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label,
                             final String[] args) {
        if (!this.enabled()) {
            this.plugin.messages().send(sender, "general.module-disabled");
            return true;
        }
        if (sender instanceof Player && !sender.hasPermission(SELF_PERMISSION)) {
            this.plugin.messages().send(sender, "general.no-permission");
            return true;
        }
        if (sender instanceof Player player && this.handleLibraryCommand(player, args)) {
            return true;
        }
        if (args.length < 1 || args.length > 2) {
            this.sendUsage(sender);
            return true;
        }

        SkinRequest request = this.resolveRequest(sender, args);
        if (request == null) {
            return true;
        }
        if (!request.changingOther() && !this.service.canChangeSkin(request.target())) {
            return true;
        }

        String requested = request.skinName();
        if (requested.equalsIgnoreCase("reset")) {
            this.service.reset(sender, request.target(), request.changingOther());
            return true;
        }
        if (requested.equalsIgnoreCase("random")) {
            this.service.beginCooldown(request.target());
            this.service.applyRandom(sender, request.target(), request.changingOther());
            return true;
        }
        if (!SkinSettings.isValidMinecraftName(requested)) {
            this.plugin.messages().send(sender, "skin.invalid-name");
            return true;
        }

        this.service.beginCooldown(request.target());
        this.service.applyNamed(sender, request.target(), requested, request.changingOther(), true);
        return true;
    }

    private boolean handleLibraryCommand(final Player player, final String[] args) {
        if (args.length == 0) {
            return false;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "favorites" -> this.openMenu(player, args, SkinMenu.Type.FAVORITES);
            case "history" -> this.openMenu(player, args, SkinMenu.Type.HISTORY);
            case "clearhistory" -> this.clearHistory(player, args);
            case "save" -> this.saveFavorite(player, args);
            case "unsave" -> this.removeFavorite(player, args);
            default -> false;
        };
    }

    private boolean openMenu(final Player player, final String[] args, final SkinMenu.Type type) {
        if (args.length != 1) {
            this.sendUsage(player);
            return true;
        }
        this.menu.open(player, type, 0);
        return true;
    }

    private boolean clearHistory(final Player player, final String[] args) {
        if (args.length != 1) {
            this.sendUsage(player);
            return true;
        }
        this.repository.clearHistory(player.getName());
        this.repository.save();
        this.plugin.messages().send(player, "skin.history-cleared");
        return true;
    }

    private boolean saveFavorite(final Player player, final String[] args) {
        if (args.length != 2) {
            this.sendUsage(player);
            return true;
        }
        String requested = args[1];
        if (!SkinSettings.isValidMinecraftName(requested)) {
            this.plugin.messages().send(player, "skin.invalid-name");
            return true;
        }

        boolean unlimited = player.hasPermission("emsichill.skin.favorites.unlimited");
        if (!unlimited && this.repository.favorites(player.getName()).size() >= this.settings.favoriteLimit()) {
            this.sendFavoriteLimit(player);
            return true;
        }

        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            SkinTexture texture;
            try {
                texture = this.provider.findByName(requested);
            } catch (IOException exception) {
                this.runSync(() -> this.plugin.messages().send(player, "skin.mojang-unavailable"));
                return;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
            this.runSync(() -> this.finishFavoriteSave(player, requested, texture, unlimited));
        });
        return true;
    }

    private void finishFavoriteSave(final Player player, final String requested, final SkinTexture texture,
                                    final boolean unlimited) {
        if (texture == null) {
            this.plugin.messages().send(player, "skin.not-found", "{skin}", requested);
            return;
        }

        FavoriteResult result = this.repository.addFavorite(
            player.getName(), texture.name(), this.settings.favoriteLimit(), unlimited
        );
        if (result == FavoriteResult.LIMIT_REACHED) {
            this.sendFavoriteLimit(player);
            return;
        }

        this.repository.save();
        String message = result == FavoriteResult.ADDED ? "skin.favorite-saved" : "skin.favorite-exists";
        this.plugin.messages().send(player, message, "{skin}", texture.name());
    }

    private boolean removeFavorite(final Player player, final String[] args) {
        if (args.length != 2) {
            this.sendUsage(player);
            return true;
        }
        this.removeFavorite(player, args[1]);
        return true;
    }

    private void removeFavorite(final Player player, final String skinName) {
        boolean removed = this.repository.removeFavorite(player.getName(), skinName);
        if (removed) {
            this.repository.save();
        }
        String message = removed ? "skin.favorite-removed" : "skin.favorite-not-found";
        this.plugin.messages().send(player, message, "{skin}", skinName);
    }

    private SkinRequest resolveRequest(final CommandSender sender, final String[] args) {
        if (args.length == 1) {
            if (!(sender instanceof Player player)) {
                this.plugin.messages().send(sender, "skin.admin-usage");
                return null;
            }
            return new SkinRequest(player, args[0], false);
        }

        if (!sender.hasPermission(OTHERS_PERMISSION)) {
            this.plugin.messages().send(sender, "general.no-permission");
            return null;
        }
        Player target = this.findOnline(args[0]);
        if (target == null) {
            this.plugin.messages().send(sender, "skin.player-offline");
            return null;
        }
        return new SkinRequest(target, args[1], true);
    }

    @EventHandler
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        SkinMenu.State state = this.menu.state(event, player);
        if (state == null) {
            return;
        }

        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot == 49) {
            player.closeInventory();
            return;
        }
        if (slot == 45 && state.page() > 0) {
            this.menu.open(player, state.type(), state.page() - 1);
            return;
        }
        if (slot == 53) {
            this.menu.open(player, state.type(), state.page() + 1);
            return;
        }

        String skinName = state.skinAt(slot);
        if (skinName == null) {
            return;
        }
        if (state.type() == SkinMenu.Type.FAVORITES && event.getClick() == ClickType.RIGHT) {
            this.removeFavorite(player, skinName);
            this.menu.open(player, SkinMenu.Type.FAVORITES, state.page());
            return;
        }

        player.closeInventory();
        this.service.applyNamed(player, player, skinName, false, true);
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        if (this.enabled()) {
            this.service.restoreSelectedSkin(event.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        this.service.forgetPlayer(event.getPlayer());
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias,
                                      final String[] args) {
        if (args.length == 1) {
            List<String> values = new ArrayList<>(SUBCOMMANDS);
            if (sender.hasPermission(OTHERS_PERMISSION)) {
                Bukkit.getOnlinePlayers().stream().map(Player::getName).forEach(values::add);
            }
            return filter(values, args[0]);
        }
        if (args.length == 2 && List.of("save", "unsave").contains(args[0].toLowerCase(Locale.ROOT))) {
            return Collections.emptyList();
        }
        if (args.length == 2 && sender.hasPermission(OTHERS_PERMISSION)) {
            return filter(List.of("random", "reset"), args[1]);
        }
        return Collections.emptyList();
    }

    private boolean enabled() {
        return this.plugin.moduleEnabled("skins");
    }

    private Player findOnline(final String name) {
        return Bukkit.getOnlinePlayers().stream()
            .filter(player -> player.getName().equalsIgnoreCase(name))
            .findFirst()
            .orElse(null);
    }

    private void sendFavoriteLimit(final Player player) {
        this.plugin.messages().send(
            player, "skin.favorite-limit", "{limit}", Integer.toString(this.settings.favoriteLimit())
        );
    }

    private void sendUsage(final CommandSender sender) {
        this.plugin.messages().send(sender, "skin.usage");
        if (sender.hasPermission(OTHERS_PERMISSION)) {
            this.plugin.messages().send(sender, "skin.admin-usage");
        }
    }

    private void runSync(final Runnable action) {
        Bukkit.getScheduler().runTask(this.plugin, action);
    }

    private static List<String> filter(final List<String> values, final String prefix) {
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        return values.stream()
            .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lowerPrefix))
            .distinct()
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
    }

    private record SkinRequest(Player target, String skinName, boolean changingOther) {
    }
}
