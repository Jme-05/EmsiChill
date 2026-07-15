package me.jaime.emsichill.util;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import me.jaime.emsichill.Main;

/** Registra acciones administrativas y de seguridad cuando la auditoría está habilitada. */
public final class AuditLogger {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Main plugin;
    private final File logFile;

    public AuditLogger(final Main plugin) {
        this.plugin = plugin;
        this.logFile = new File(plugin.getDataFolder(), "logs/audit.log");
    }

    public void log(final String action, final String details) {
        if (!this.plugin.settings().getBoolean("audit-log.enabled", true)) {
            return;
        }

        String cleanDetails = details.replace('\r', ' ').replace('\n', ' ');
        String line = "[" + TIME_FORMAT.format(LocalDateTime.now()) + "] " + action + " | " + cleanDetails + System.lineSeparator();
        this.plugin.dataStore().appendAsync(this.logFile, line);
    }
}