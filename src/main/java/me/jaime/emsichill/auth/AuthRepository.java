package me.jaime.emsichill.auth;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import me.jaime.emsichill.Main;

/**
 * Fuente de verdad de cuentas y sesiones. Conserva el formato YAML existente y migra el archivo
 * de usuarios antiguo cuando todavía está presente.
 */
public final class AuthRepository {
    private final Main plugin;
    private final File usersFile;
    private final File sessionsFile;
    private final Map<String, PasswordRecord> users = new ConcurrentHashMap<>();
    private final Map<String, SessionRecord> sessions = new ConcurrentHashMap<>();

    public AuthRepository(final Main plugin, final int defaultHashIterations) {
        this.plugin = plugin;
        this.usersFile = new File(plugin.getDataFolder(), "AuthenticationManager/users.yml");
        this.sessionsFile = new File(plugin.getDataFolder(), "AuthenticationManager/sessions.yml");
        this.loadUsers(defaultHashIterations);
        this.loadSessions();
    }

    public int accountCount() {
        return this.users.size();
    }

    public boolean hasAccount(final String name) {
        return this.users.containsKey(key(name));
    }

    public PasswordRecord account(final String name) {
        return this.users.get(key(name));
    }

    public PasswordRecord putIfAbsent(final String name, final PasswordRecord record) {
        return this.users.putIfAbsent(key(name), record);
    }

    public void putAccount(final String name, final PasswordRecord record) {
        this.users.put(key(name), record);
    }

    public PasswordRecord removeAccount(final String name) {
        return this.users.remove(key(name));
    }

    public List<String> accountNames() {
        List<String> names = new ArrayList<>();
        for (PasswordRecord record : this.users.values()) {
            names.add(record.name());
        }
        return names;
    }

    public SessionRecord session(final String name) {
        return this.sessions.get(key(name));
    }

    public void putSession(final String name, final SessionRecord session) {
        this.sessions.put(key(name), session);
    }

    public void removeSession(final String name) {
        this.sessions.remove(key(name));
    }

    public boolean replaceIfCurrent(final String name, final PasswordRecord current, final PasswordRecord replacement) {
        return this.users.replace(key(name), current, replacement);
    }

    public boolean saveUsers() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, PasswordRecord> entry : this.users.entrySet()) {
            String path = "users." + entry.getKey();
            PasswordRecord record = entry.getValue();
            yaml.set(path + ".name", record.name());
            yaml.set(path + ".iterations", record.iterations());
            yaml.set(path + ".salt", record.salt());
            yaml.set(path + ".hash", record.hash());
        }
        return this.plugin.dataStore().saveNow(this.usersFile, yaml);
    }

    public boolean saveSessions() {
        YamlConfiguration yaml = new YamlConfiguration();
        long now = Instant.now().getEpochSecond();
        this.sessions.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        for (Map.Entry<String, SessionRecord> entry : this.sessions.entrySet()) {
            yaml.set("sessions." + entry.getKey() + ".address-hash", entry.getValue().addressHash());
            yaml.set("sessions." + entry.getKey() + ".expires-at", entry.getValue().expiresAt());
        }
        return this.plugin.dataStore().saveNow(this.sessionsFile, yaml);
    }

    private void loadUsers(final int defaultHashIterations) {
        File source = this.usersFile;
        // Solo se consulta la ruta antigua cuando todavía no existe el archivo modular actual.
        if (!source.exists()) {
            File legacy = new File(this.plugin.getDataFolder(), "users.yml");
            if (legacy.exists()) {
                source = legacy;
            }
        }
        if (!source.exists()) {
            return;
        }
        YamlConfiguration yaml = this.plugin.dataStore().load(source);
        ConfigurationSection section = yaml.getConfigurationSection("users");
        if (section == null) {
            return;
        }
        for (String entry : section.getKeys(false)) {
            String path = "users." + entry;
            String name = yaml.getString(path + ".name");
            String salt = yaml.getString(path + ".salt");
            String hash = yaml.getString(path + ".hash");
            int iterations = yaml.getInt(path + ".iterations", defaultHashIterations);
            if (name != null && salt != null && hash != null) {
                this.users.put(key(entry), new PasswordRecord(name, iterations, salt, hash));
            }
        }
        if (!source.equals(this.usersFile)) {
            this.saveUsers();
        }
    }

    private void loadSessions() {
        if (!this.sessionsFile.exists()) {
            return;
        }
        YamlConfiguration yaml = this.plugin.dataStore().load(this.sessionsFile);
        ConfigurationSection section = yaml.getConfigurationSection("sessions");
        if (section == null) {
            return;
        }
        long now = Instant.now().getEpochSecond();
        for (String entry : section.getKeys(false)) {
            String path = "sessions." + entry;
            String addressHash = yaml.getString(path + ".address-hash");
            long expiresAt = yaml.getLong(path + ".expires-at");
            if (addressHash != null && expiresAt > now) {
                this.sessions.put(key(entry), new SessionRecord(addressHash, expiresAt));
            }
        }
    }

    private static String key(final String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}