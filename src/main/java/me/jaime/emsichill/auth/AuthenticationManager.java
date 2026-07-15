package me.jaime.emsichill.auth;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import me.jaime.emsichill.Main;
import me.jaime.emsichill.config.ConfigFile;
import me.jaime.emsichill.util.CommandSuggestions;

/**
 * Coordina registro, inicio de sesión, sesiones temporales y estado autenticado. El cifrado y
 * la persistencia se delegan para mantener aquí solamente el flujo de autenticación.
 */
public final class AuthenticationManager implements CommandExecutor, TabCompleter, Listener {
    private static final int MINIMUM_SAFE_HASH_ITERATIONS = 50_000;

    private final Main plugin;
    private final PasswordHasher passwordHasher = new PasswordHasher();
    private final Set<UUID> authenticated = ConcurrentHashMap.newKeySet();
    private final Set<UUID> processing = ConcurrentHashMap.newKeySet();
    private final Set<UUID> internalTeleports = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> failedAttempts = new ConcurrentHashMap<>();
    private final Map<String, Long> loginLockouts = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> timeoutTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Location> returnLocations = new ConcurrentHashMap<>();
    private final AuthRepository repository;

    private ConfigFile configFile;
    private int hashIterations;
    private int minPasswordLength;
    private int maxPasswordLength;
    private int maxLoginAttempts;
    private long loginLockoutSeconds;
    private long loginTimeoutTicks;
    private Set<String> allowedCommands = ConcurrentHashMap.newKeySet();
    private boolean configurationLoaded;
    private boolean moduleWasEnabled;

    public AuthenticationManager(final Main plugin) {
        this.plugin = plugin;
        this.reloadConfiguration();
        this.repository = new AuthRepository(plugin, this.hashIterations);
    }

    public void reloadConfiguration() {
        boolean previouslyEnabled = this.moduleWasEnabled;
        if (this.configFile == null) {
            this.configFile = new ConfigFile(this.plugin, "AuthenticationManager/config.yml");
        } else {
            this.configFile.reload();
        }
        YamlConfiguration config = this.configFile.yaml();
        this.hashIterations = Math.max(MINIMUM_SAFE_HASH_ITERATIONS,
            config.getInt("settings.password-hash-iterations", 210_000));
        this.minPasswordLength = Math.max(1, config.getInt("settings.min-password-length", 6));
        this.maxPasswordLength = Math.max(this.minPasswordLength,
            config.getInt("settings.max-password-length", 64));
        this.maxLoginAttempts = Math.max(1, config.getInt("settings.max-login-attempts", 5));
        this.loginLockoutSeconds = Math.max(5L, config.getLong("settings.login-lockout-seconds", 30L));
        this.loginTimeoutTicks = Math.max(5L, config.getLong("settings.login-timeout-seconds", 60L)) * 20L;

        List<String> configuredCommands = new ArrayList<>(config.getStringList("blocking.allowed-commands"));
        boolean removedSpanishAliases = configuredCommands.removeIf(value -> value.equalsIgnoreCase("iniciar")
            || value.equalsIgnoreCase("registrar"));
        if (removedSpanishAliases) {
            config.set("blocking.allowed-commands", configuredCommands);
            this.configFile.save();
        }
        Set<String> commands = ConcurrentHashMap.newKeySet();
        for (String value : configuredCommands) {
            commands.add(value.toLowerCase(Locale.ROOT));
        }
        Collections.addAll(commands, "login", "l", "register", "reg");
        this.allowedCommands = commands;

        boolean currentlyEnabled = this.enabled();
        if (!currentlyEnabled || !this.hideUnauthenticated()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                this.revealPlayer(player);
            }
        }
        if (this.configurationLoaded && previouslyEnabled && !currentlyEnabled) {
            for (UUID id : new ArrayList<>(this.timeoutTasks.keySet())) this.cancelTimeout(id);
            for (Player player : Bukkit.getOnlinePlayers()) this.returnFromAuthenticationLocation(player);
            this.authenticated.clear();
        } else if (this.configurationLoaded && !previouslyEnabled && currentlyEnabled) {
            for (Player player : Bukkit.getOnlinePlayers()) this.beginAuthentication(player);
        }
        this.moduleWasEnabled = currentlyEnabled;
        this.configurationLoaded = true;
        for (Player player : Bukkit.getOnlinePlayers()) {
            this.refreshCommands(player);
        }
    }

    public void start() {
        if (!this.enabled()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            this.beginAuthentication(player);
        }
    }

    public void stop() {
        for (BukkitTask task : this.timeoutTasks.values()) {
            task.cancel();
        }
        this.timeoutTasks.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            this.revealPlayer(player);
        }
        this.authenticated.clear();
        this.processing.clear();
        this.failedAttempts.clear();
        this.returnLocations.clear();
        this.repository.saveUsers();
        this.repository.saveSessions();
    }

    public int registeredCount() { return this.repository.accountCount(); }
    public int authenticatedCount() { return this.authenticated.size(); }
    public void persistData() { this.repository.saveUsers(); this.repository.saveSessions(); }

    private boolean enabled() {
        return this.plugin.moduleEnabled("authentication");
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (!this.enabled()) {
            this.plugin.messages().send(sender, "general.module-disabled");
            return true;
        }

        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "register" -> this.handleRegister(sender, args);
            case "login" -> this.handleLogin(sender, args);
            case "changepassword" -> this.handleChangePassword(sender, args);
            case "unregister" -> this.handleUnregister(sender, args);
            case "auth" -> this.handleAdmin(sender, args);
            default -> true;
        };
    }

    private Player requirePlayer(final CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        this.plugin.messages().send(sender, "general.only-players");
        return null;
    }

    private boolean handleRegister(final CommandSender sender, final String[] args) {
        Player player = this.requirePlayer(sender);
        if (player == null) {
            return true;
        }
        String key = this.userKey(player.getName());
        if (this.repository.hasAccount(key)) {
            this.plugin.messages().send(player, "auth.already-registered");
            return true;
        }
        if (args.length != 2) {
            this.plugin.messages().send(player, "auth.register-usage");
            return true;
        }
        if (!args[0].equals(args[1])) {
            this.plugin.messages().send(player, "auth.passwords-differ");
            return true;
        }
        if (!this.validPassword(args[0], player)) {
            return true;
        }
        if (!this.beginProcessing(player)) {
            return true;
        }

        this.createRecordAsync(player.getName(), args[0].toCharArray(), record -> {
            this.processing.remove(player.getUniqueId());
            if (record == null) {
                this.plugin.messages().send(player, "auth.error");
                return;
            }
            if (!player.isOnline() || this.repository.putIfAbsent(key, record) != null) {
                return;
            }
            if (!this.repository.saveUsers()) {
                this.repository.removeAccount(key);
                this.plugin.messages().send(player, "auth.save-error");
                return;
            }
            this.authenticate(player, false);
            this.plugin.messages().send(player, "auth.registered");
            this.plugin.audit().log("REGISTER", "player=" + player.getName() + " ip=" + this.address(player));
        });
        return true;
    }

    private boolean handleLogin(final CommandSender sender, final String[] args) {
        Player player = this.requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (this.authenticated.contains(player.getUniqueId())) {
            this.plugin.messages().send(player, "auth.already-logged-in");
            return true;
        }
        PasswordRecord record = this.repository.account(player.getName());
        if (record == null) {
            this.plugin.messages().send(player, "auth.must-register");
            return true;
        }
        if (args.length != 1) {
            this.plugin.messages().send(player, "auth.login-usage");
            return true;
        }
        String loginKey = this.userKey(player.getName());
        long remainingLock = this.loginLockouts.getOrDefault(loginKey, 0L) - Instant.now().getEpochSecond();
        if (remainingLock > 0L) {
            this.plugin.messages().send(player, "auth.login-locked", "{seconds}", Long.toString(remainingLock));
            return true;
        }
        this.loginLockouts.remove(loginKey);
        if (!this.beginProcessing(player)) {
            return true;
        }
        this.verifyAsync(player, record, args[0].toCharArray(), matches -> {
            this.processing.remove(player.getUniqueId());
            if (!player.isOnline()) {
                return;
            }
            if (matches) {
                this.authenticate(player, false);
                this.plugin.messages().send(player, "auth.logged-in");
                this.plugin.audit().log("LOGIN_SUCCESS", "player=" + player.getName());
                return;
            }
            int attempts = this.failedAttempts.merge(loginKey, 1, Integer::sum);
            int remaining = this.maxLoginAttempts - attempts;
            this.plugin.audit().log("LOGIN_FAILURE", "player=" + player.getName()
                + " remaining=" + Math.max(0, remaining));
            if (remaining <= 0) {
                this.failedAttempts.remove(loginKey);
                this.loginLockouts.put(loginKey, Instant.now().getEpochSecond() + this.loginLockoutSeconds);
                player.kick(this.plugin.messages().component("auth.too-many-attempts"));
            } else {
                this.plugin.messages().send(player, "auth.wrong-password", "{remaining}", Integer.toString(remaining));
            }
        });
        return true;
    }

    private boolean handleChangePassword(final CommandSender sender, final String[] args) {
        Player player = this.requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (!this.isAuthenticated(player)) {
            this.plugin.messages().send(player, "auth.login-prompt");
            return true;
        }
        if (args.length != 3) {
            this.plugin.messages().send(player, "auth.change-usage");
            return true;
        }
        if (!args[1].equals(args[2])) {
            this.plugin.messages().send(player, "auth.passwords-differ");
            return true;
        }
        if (!this.validPassword(args[1], player) || !this.beginProcessing(player)) {
            return true;
        }
        String key = this.userKey(player.getName());
        PasswordRecord current = this.repository.account(key);
        this.verifyAsync(player, current, args[0].toCharArray(), matches -> {
            if (!matches) {
                this.processing.remove(player.getUniqueId());
                this.plugin.messages().send(player, "auth.current-password-wrong");
                return;
            }
            this.createRecordAsync(player.getName(), args[1].toCharArray(), replacement -> {
                this.processing.remove(player.getUniqueId());
                if (replacement == null) {
                    this.plugin.messages().send(player, "auth.error");
                    return;
                }
                this.repository.putAccount(key, replacement);
                this.repository.removeSession(key);
                if (this.repository.saveUsers()) {
                    this.rememberSession(player);
                    this.plugin.messages().send(player, "auth.password-changed");
                    this.plugin.audit().log("PASSWORD_CHANGE", "player=" + player.getName());
                } else {
                    this.repository.putAccount(key, current);
                    this.plugin.messages().send(player, "auth.save-error");
                }
            });
        });
        return true;
    }

    private boolean handleUnregister(final CommandSender sender, final String[] args) {
        Player player = this.requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (!this.isAuthenticated(player)) {
            this.plugin.messages().send(player, "auth.login-prompt");
            return true;
        }
        if (args.length != 1) {
            this.plugin.messages().send(player, "auth.unregister-usage");
            return true;
        }
        if (!this.beginProcessing(player)) {
            return true;
        }
        String key = this.userKey(player.getName());
        this.verifyAsync(player, this.repository.account(key), args[0].toCharArray(), matches -> {
            this.processing.remove(player.getUniqueId());
            if (!matches) {
                this.plugin.messages().send(player, "auth.current-password-wrong");
                return;
            }
            this.repository.removeAccount(key);
            this.repository.removeSession(key);
            this.repository.saveUsers();
            this.repository.saveSessions();
            this.authenticated.remove(player.getUniqueId());
            this.plugin.audit().log("UNREGISTER", "player=" + player.getName());
            this.plugin.messages().send(player, "auth.unregistered");
            this.beginAuthentication(player);
        });
        return true;
    }

    private boolean handleAdmin(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("emsichill.auth.admin")) {
            this.plugin.messages().send(sender, "general.no-permission");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            this.plugin.reloadPlugin();
            this.plugin.messages().send(sender, "general.reloaded");
            this.plugin.audit().log("AUTH_RELOAD", "actor=" + sender.getName());
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("unregister")) {
            String key = this.userKey(args[1]);
            PasswordRecord removed = this.repository.removeAccount(key);
            this.repository.removeSession(key);
            if (removed == null) {
                this.plugin.messages().send(sender, "auth.account-not-found");
                return true;
            }
            this.repository.saveUsers();
            this.repository.saveSessions();
            Player online = this.findOnline(args[1]);
            if (online != null) {
                this.authenticated.remove(online.getUniqueId());
                this.beginAuthentication(online);
            }
            this.plugin.messages().send(sender, "auth.admin-unregistered", "{player}", removed.name());
            this.plugin.audit().log("ADMIN_UNREGISTER", "actor=" + sender.getName() + " player=" + removed.name());
            return true;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("changepassword")) {
            String key = this.userKey(args[1]);
            PasswordRecord current = this.repository.account(key);
            if (current == null) {
                this.plugin.messages().send(sender, "auth.account-not-found");
                return true;
            }
            if (!this.validPassword(args[2], sender)) {
                return true;
            }
            this.createRecordAsync(current.name(), args[2].toCharArray(), replacement -> {
                if (replacement == null) {
                    this.plugin.messages().send(sender, "auth.error");
                    return;
                }
                this.repository.putAccount(key, replacement);
                this.repository.removeSession(key);
                this.repository.saveUsers();
                this.repository.saveSessions();
                this.plugin.messages().send(sender, "auth.admin-password-changed", "{player}", current.name());
                this.plugin.audit().log("ADMIN_PASSWORD_CHANGE", "actor=" + sender.getName() + " player=" + current.name());
            });
            return true;
        }
        this.plugin.messages().send(sender, "auth.admin-usage");
        return true;
    }

    private boolean validPassword(final String password, final CommandSender sender) {
        if (password.length() < this.minPasswordLength || password.length() > this.maxPasswordLength) {
            this.plugin.messages().send(sender, "auth.invalid-password-length",
                "{min}", Integer.toString(this.minPasswordLength), "{max}", Integer.toString(this.maxPasswordLength));
            return false;
        }
        return true;
    }

    private boolean beginProcessing(final Player player) {
        if (this.processing.add(player.getUniqueId())) {
            return true;
        }
        this.plugin.messages().send(player, "auth.processing");
        return false;
    }

    // El hash se calcula fuera del hilo principal para no detener los ticks del servidor.
    private void createRecordAsync(
        final String playerName, final char[] password,
        final java.util.function.Consumer<PasswordRecord> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            try {
                PasswordRecord record = this.passwordHasher.create(playerName, password, this.hashIterations);
                Bukkit.getScheduler().runTask(this.plugin, () -> callback.accept(record));
            } catch (GeneralSecurityException exception) {
                this.plugin.getLogger().severe("No se pudo cifrar una contraseña: " + exception.getMessage());
                Bukkit.getScheduler().runTask(this.plugin, () -> callback.accept(null));
            } finally {
                Arrays.fill(password, '\0');
            }
        });
    }

    private void verifyAsync(final Player player, final PasswordRecord record, final char[] password,
                             final java.util.function.Consumer<Boolean> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            boolean matches = false;
            PasswordRecord upgraded = null;
            if (record != null) {
                try {
                    PasswordHasher.Verification verification = this.passwordHasher.verify(
                        record, password, this.hashIterations);
                    matches = verification.matches();
                    upgraded = verification.upgradedRecord();
                } catch (GeneralSecurityException | IllegalArgumentException exception) {
                    this.plugin.getLogger().warning("No se pudo comprobar la cuenta " + player.getName());
                }
            }
            Arrays.fill(password, '\0');
            boolean result = matches;
            PasswordRecord upgradedRecord = upgraded;
            Bukkit.getScheduler().runTask(this.plugin, () -> {
                if (upgradedRecord != null
                    && this.repository.replaceIfCurrent(player.getName(), record, upgradedRecord)) {
                    this.repository.saveUsers();
                }
                callback.accept(result);
            });
        });
    }

    // Prepara visibilidad, bloqueo y tiempo límite antes de solicitar credenciales.
    private void beginAuthentication(final Player player) {
        if (!this.enabled()) {
            this.authenticated.add(player.getUniqueId());
            this.revealPlayer(player);
            return;
        }
        UUID id = player.getUniqueId();
        this.cancelTimeout(id);
        this.authenticated.remove(id);
        this.processing.remove(id);
        this.failedAttempts.remove(this.userKey(player.getName()));
        this.loginLockouts.remove(this.userKey(player.getName()));

        if (this.restoreSession(player)) {
            this.authenticate(player, true);
            this.plugin.messages().send(player, "auth.session-restored");
            return;
        }

        if (this.hideUnauthenticated()) {
            this.hidePlayer(player);
        }
        this.moveToAuthenticationLocation(player);
        this.timeoutTasks.put(id, Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            this.timeoutTasks.remove(id);
            if (player.isOnline() && !this.isAuthenticated(player)) {
                player.kick(this.plugin.messages().component("auth.timeout"));
            }
        }, this.loginTimeoutTicks));

        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (!player.isOnline() || this.isAuthenticated(player)) {
                return;
            }
            this.plugin.messages().send(player, this.repository.hasAccount(player.getName())
                ? "auth.login-prompt" : "auth.register-prompt");
        }, 10L);
    }

    private void authenticate(final Player player, final boolean restoredSession) {
        this.authenticated.add(player.getUniqueId());
        this.failedAttempts.remove(this.userKey(player.getName()));
        this.loginLockouts.remove(this.userKey(player.getName()));
        this.cancelTimeout(player.getUniqueId());
        this.revealPlayer(player);
        this.plugin.refreshStaffVisibility();
        this.returnFromAuthenticationLocation(player);
        this.refreshCommands(player);
        if (!restoredSession) {
            this.rememberSession(player);
        }
    }

    private void refreshCommands(final Player player) {
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (player.isOnline()) {
                player.updateCommands();
            }
        });
    }

    private void cancelTimeout(final UUID id) {
        BukkitTask task = this.timeoutTasks.remove(id);
        if (task != null) {
            task.cancel();
        }
    }

    private boolean isAuthenticated(final Player player) {
        return !this.enabled() || this.authenticated.contains(player.getUniqueId());
    }

    private boolean hideUnauthenticated() {
        return this.configFile.yaml().getBoolean("visibility.hide-until-authenticated", true);
    }

    private void hidePlayer(final Player player) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player)) {
                other.hidePlayer(this.plugin, player);
                player.hidePlayer(this.plugin, other);
            }
        }
    }

    private void revealPlayer(final Player player) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player)) {
                if (!this.enabled() || !this.hideUnauthenticated() || this.isAuthenticated(other)) {
                    other.showPlayer(this.plugin, player);
                    player.showPlayer(this.plugin, other);
                } else {
                    other.hidePlayer(this.plugin, player);
                    player.hidePlayer(this.plugin, other);
                }
            }
        }
    }

    private void moveToAuthenticationLocation(final Player player) {
        if (!this.configFile.yaml().getBoolean("authentication-location.enabled", false)) {
            return;
        }
        World world = Bukkit.getWorld(this.configFile.yaml().getString("authentication-location.world", "world"));
        if (world == null) {
            this.plugin.getLogger().warning("El mundo del punto de autenticación no existe.");
            return;
        }
        this.returnLocations.put(player.getUniqueId(), player.getLocation().clone());
        Location target = new Location(world,
            this.configFile.yaml().getDouble("authentication-location.x"),
            this.configFile.yaml().getDouble("authentication-location.y"),
            this.configFile.yaml().getDouble("authentication-location.z"),
            (float) this.configFile.yaml().getDouble("authentication-location.yaw"),
            (float) this.configFile.yaml().getDouble("authentication-location.pitch"));
        this.internalTeleport(player, target);
    }

    private void returnFromAuthenticationLocation(final Player player) {
        Location location = this.returnLocations.remove(player.getUniqueId());
        if (location != null) {
            this.internalTeleport(player, location);
        }
    }

    private void internalTeleport(final Player player, final Location location) {
        this.internalTeleports.add(player.getUniqueId());
        try {
            player.teleport(location);
        } finally {
            this.internalTeleports.remove(player.getUniqueId());
        }
    }

    // Una sesión solo se restaura si sigue vigente y coincide con su huella guardada.
    private boolean restoreSession(final Player player) {
        if (!this.configFile.yaml().getBoolean("sessions.enabled", false)) {
            return false;
        }
        SessionRecord session = this.repository.session(player.getName());
        if (session == null || session.expiresAt() < Instant.now().getEpochSecond()
            || !session.addressHash().equals(this.hashAddress(this.address(player)))) {
            return false;
        }
        return this.repository.hasAccount(player.getName());
    }

    private void rememberSession(final Player player) {
        if (!this.configFile.yaml().getBoolean("sessions.enabled", false)) {
            return;
        }
        long minutes = Math.max(1L, this.configFile.yaml().getLong("sessions.duration-minutes", 30L));
        this.repository.putSession(player.getName(),
            new SessionRecord(this.hashAddress(this.address(player)), Instant.now().plusSeconds(minutes * 60L).getEpochSecond()));
        this.repository.saveSessions();
    }

    private String address(final Player player) {
        return player.getAddress() == null ? "unknown" : player.getAddress().getAddress().getHostAddress();
    }

    private String hashAddress(final String address) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(address.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String userKey(final String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private Player findOnline(final String name) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().equalsIgnoreCase(name)) {
                return player;
            }
        }
        return null;
    }

    private boolean blocking(final String key) {
        return this.configFile.yaml().getBoolean("blocking." + key, true);
    }

    boolean blocks(final Player player, final String key) {
        return this.blocking(key) && !this.isAuthenticated(player);
    }

    boolean isInternalTeleport(final Player player) {
        return this.internalTeleports.contains(player.getUniqueId());
    }

    boolean isAllowedCommand(final String command) {
        int separator = command.indexOf(':');
        String plain = separator >= 0 ? command.substring(separator + 1) : command;
        return this.allowedCommands.contains(plain.toLowerCase(Locale.ROOT));
    }

    boolean shouldFilterCommandSuggestions(final Player player) {
        return this.configFile.yaml().getBoolean("blocking.command-suggestions", true)
            && !this.isAuthenticated(player);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLogin(final PlayerLoginEvent event) {
        if (!this.enabled()) {
            return;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(event.getPlayer().getUniqueId())
                && online.getName().equalsIgnoreCase(event.getPlayer().getName())) {
                event.disallow(PlayerLoginEvent.Result.KICK_OTHER, this.plugin.messages().component("auth.duplicate-online"));
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(final PlayerJoinEvent event) {
        this.beginAuthentication(event.getPlayer());
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        this.authenticated.remove(id);
        this.processing.remove(id);
        this.returnLocations.remove(id);
        this.cancelTimeout(id);
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args) {
        if (!command.getName().equalsIgnoreCase("auth") || !sender.hasPermission("emsichill.auth.admin")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return CommandSuggestions.filter(List.of("unregister", "changepassword", "reload"), args[0]);
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("reload")) {
            List<String> names = new ArrayList<>();
            names.addAll(this.repository.accountNames());
            return CommandSuggestions.filter(names, args[1]);
        }
        return Collections.emptyList();
    }

}