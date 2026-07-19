package me.jaime.emsichill;

import java.util.List;
import java.util.Objects;

import org.bukkit.Location;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import me.jaime.emsichill.auth.AuthenticationBarrier;
import me.jaime.emsichill.auth.AuthenticationManager;
import me.jaime.emsichill.commands.EmsiChillCommand;
import me.jaime.emsichill.commands.SkinCommand;
import me.jaime.emsichill.config.ConfigFile;
import me.jaime.emsichill.config.Messages;
import me.jaime.emsichill.documentation.CommandDocumentation;
import me.jaime.emsichill.grave.GraveManager;
import me.jaime.emsichill.home.HomeManager;
import me.jaime.emsichill.maintenance.MaintenanceService;
import me.jaime.emsichill.playerinfo.PlayerInfoManager;
import me.jaime.emsichill.region.RegionManager;
import me.jaime.emsichill.social.SocialManager;
import me.jaime.emsichill.staff.FreezeService;
import me.jaime.emsichill.staff.InspectionService;
import me.jaime.emsichill.staff.StaffCommand;
import me.jaime.emsichill.staff.StaffListener;
import me.jaime.emsichill.staff.StaffService;
import me.jaime.emsichill.storage.DataStore;
import me.jaime.emsichill.teleport.TeleportManager;
import me.jaime.emsichill.util.AuditLogger;
import me.jaime.emsichill.update.UpdateService;
import me.jaime.emsichill.update.UpdateNotifier;

/**
 * Punto de entrada de Paper. Construye los módulos y coordina su ciclo de vida sin contener
 * reglas propias de cada sistema.
 */
public final class Main extends JavaPlugin {
    private ConfigFile settingsFile;
    private Messages messages;
    private AuditLogger auditLogger;
    private DataStore dataStore;
    private AuthenticationManager authenticationManager;
    private SkinCommand skinCommand;
    private TeleportManager teleportManager;
    private HomeManager homeManager;
    private PlayerInfoManager playerInfoManager;
    private StaffService staffService;
    private StaffCommand staffCommand;
    private StaffListener staffListener;
    private InspectionService inspectionService;
    private FreezeService freezeService;
    private UpdateService updateService;
    private UpdateNotifier updateNotifier;
    private CommandDocumentation commandDocumentation;
    private RegionManager regionManager;
    private GraveManager graveManager;
    private SocialManager socialManager;

    @Override
    public void onEnable() {
        this.initializeInfrastructure();
        MaintenanceService maintenance = new MaintenanceService(this);
        this.initializeModules();
        this.registerCommands(maintenance);
        this.registerListeners();
        this.startModules();
        getLogger().info("EmsiChill se ha habilitado correctamente.");
    }

    private void initializeInfrastructure() {
        this.dataStore = new DataStore(getLogger());
        this.settingsFile = new ConfigFile(this, "config.yml");
        if (this.settingsFile.yaml().getString("prefix", "").equals("&8[&aEmsiChill&8] ")) {
            this.settingsFile.yaml().set("prefix", "&8[&5EmsiChill&8] ");
            this.settingsFile.save();
        }
        this.messages = new Messages(this);
        this.auditLogger = new AuditLogger(this);
    }

    private void initializeModules() {
        this.authenticationManager = new AuthenticationManager(this);
        this.skinCommand = new SkinCommand(this);
        this.teleportManager = new TeleportManager(this);
        this.homeManager = new HomeManager(this, this.teleportManager);
        this.inspectionService = new InspectionService();
        this.freezeService = new FreezeService(this);
        this.staffService = new StaffService(this);
        this.staffCommand = new StaffCommand(this, this.staffService, this.inspectionService, this.freezeService);
        this.staffListener = new StaffListener(this, this.staffService, this.inspectionService, this.freezeService);
        this.updateService = new UpdateService(this);
        this.updateNotifier = new UpdateNotifier(this, this.updateService);
        this.commandDocumentation = CommandDocumentation.load(this);
        this.playerInfoManager = new PlayerInfoManager(this);
        this.regionManager = new RegionManager(this);
        this.graveManager = new GraveManager(this);
        this.socialManager = new SocialManager(this);
    }

    private void registerCommands(final MaintenanceService maintenance) {
        this.registerAll(List.of("login", "register", "changepassword", "unregister"),
            this.authenticationManager, null);
        this.register("auth", this.authenticationManager, this.authenticationManager);
        this.register("skin", this.skinCommand, this.skinCommand);
        this.registerAll(List.of("home", "sethome", "delhome", "homes"), this.homeManager, this.homeManager);
        this.registerAll(List.of("tpa", "tpahere", "tpaccept", "tpdeny", "tpcancel", "tptoggle", "back", "rtp"),
            this.teleportManager, this.teleportManager);
        this.registerAll(List.of("playtime", "seen", "playtimetop"),
            this.playerInfoManager, this.playerInfoManager);
        this.registerAll(List.of("staffchat", "vanish", "vanishlist", "staffmode", "invsee", "enderchestsee",
            "freeze", "slay"),
            this.staffCommand, this.staffCommand);
        this.register("region", this.regionManager, this.regionManager);
        this.registerAll(List.of("grave", "deathcontrol"), this.graveManager, this.graveManager);
        this.registerAll(List.of("sit", "stand", "whereami"), this.socialManager, null);

        EmsiChillCommand adminCommand = new EmsiChillCommand(this, maintenance, this.authenticationManager,
            this.skinCommand, this.teleportManager, this.homeManager, this.playerInfoManager, this.staffService,
            this.regionManager, this.graveManager, this.updateService, this.commandDocumentation);
        this.register("emsichill", adminCommand, adminCommand);
    }

    private void registerListeners() {
        var pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(this.authenticationManager, this);
        pluginManager.registerEvents(new AuthenticationBarrier(this, this.authenticationManager), this);
        pluginManager.registerEvents(this.skinCommand, this);
        pluginManager.registerEvents(this.teleportManager, this);
        pluginManager.registerEvents(this.playerInfoManager, this);
        pluginManager.registerEvents(this.staffListener, this);
        pluginManager.registerEvents(this.regionManager, this);
        pluginManager.registerEvents(this.graveManager, this);
        pluginManager.registerEvents(this.socialManager, this);
        pluginManager.registerEvents(this.updateNotifier, this);
    }

    private void startModules() {
        this.authenticationManager.start();
        this.skinCommand.start();
        this.playerInfoManager.start();
        this.staffService.start();
        this.graveManager.start();
        this.socialManager.start();
        this.updateNotifier.start();
    }

    @Override
    public void onDisable() {
        if (this.authenticationManager != null) this.authenticationManager.stop();
        if (this.skinCommand != null) this.skinCommand.stop();
        if (this.teleportManager != null) this.teleportManager.stop();
        if (this.homeManager != null) this.homeManager.stop();
        if (this.playerInfoManager != null) this.playerInfoManager.stop();
        if (this.staffService != null) this.staffService.stop();
        if (this.inspectionService != null) this.inspectionService.clear();
        if (this.freezeService != null) this.freezeService.clear();
        if (this.regionManager != null) this.regionManager.stop();
        if (this.graveManager != null) this.graveManager.stop();
        if (this.socialManager != null) this.socialManager.stop();
        if (this.updateNotifier != null) this.updateNotifier.stop();
        if (this.dataStore != null) this.dataStore.close();
        getLogger().info("EmsiChill se ha deshabilitado correctamente.");
    }

    private void registerAll(
        final List<String> names,
        final CommandExecutor executor,
        final TabCompleter completer
    ) {
        for (String name : names) {
            this.register(name, executor, completer);
        }
    }

    private void register(final String name, final CommandExecutor executor, final TabCompleter completer) {
        PluginCommand command = Objects.requireNonNull(getCommand(name), "Falta /" + name + " en plugin.yml");
        command.setExecutor(executor);
        if (completer != null) {
            command.setTabCompleter(completer);
        }
    }

    public YamlConfiguration settings() { return this.settingsFile.yaml(); }
    public Messages messages() { return this.messages; }
    public AuditLogger audit() { return this.auditLogger; }
    public DataStore dataStore() { return this.dataStore; }

    public boolean moduleEnabled(final String module) {
        return this.settings().getBoolean("modules." + module, true);
    }

    public boolean isVanished(final Player player) {
        return this.staffService != null && this.staffService.isVanished(player);
    }

    public void refreshStaffVisibility() {
        if (this.staffService != null) this.staffService.refreshVisibility();
    }

    public void rememberGraveBackLocation(final Player player, final Location graveLocation) {
        if (this.teleportManager != null) this.teleportManager.rememberDeathGrave(player, graveLocation);
    }

    public void reloadPlugin() {
        this.settingsFile.reload();
        this.messages.reload();
        this.authenticationManager.reloadConfiguration();
        this.skinCommand.reloadConfiguration();
        this.teleportManager.reloadConfiguration();
        this.homeManager.reloadConfiguration();
        this.playerInfoManager.reloadConfiguration();
        this.staffService.reloadConfiguration();
        this.regionManager.reloadConfiguration();
        this.graveManager.reloadConfiguration();
        this.socialManager.reloadConfiguration();
        this.updateNotifier.reloadConfiguration();
    }
}
