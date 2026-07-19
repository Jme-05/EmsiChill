package me.jaime.emsichill.staff;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import me.jaime.emsichill.Main;
import me.jaime.emsichill.config.ConfigFile;

/** Aplica silencios y advertencias, conserva su historial y elimina sanciones vencidas. */
public final class ModerationService {
    private final ModerationRepository repository;
    private final Map<String, PlayerModeration> records = new LinkedHashMap<>();
    private ConfigFile configFile;

    public ModerationService(final Main plugin) {
        this.repository = new ModerationRepository(plugin);
        this.records.putAll(this.repository.load());
        this.configFile = new ConfigFile(plugin, "Staff/config.yml");
    }

    public synchronized void reloadConfiguration() {
        this.configFile.reload();
    }

    public synchronized MuteRecord mute(
        final String playerName,
        final String moderator,
        final long durationMillis
    ) {
        long now = System.currentTimeMillis();
        long expiresAt = durationMillis == 0L ? 0L : now + durationMillis;
        String reason = durationMillis == 0L
            ? "Silencio permanente"
            : "Silencio durante " + MuteDuration.format(durationMillis);
        MuteRecord mute = new MuteRecord(now, expiresAt, moderator, reason);
        PlayerModeration record = this.record(playerName);
        List<ModerationEntry> history = new ArrayList<>(record.history());
        history.add(new ModerationEntry(now, ModerationAction.MUTE, moderator, reason));
        this.records.put(this.key(playerName), new PlayerModeration(playerName, mute, this.trim(history)));
        this.saveData();
        return mute;
    }

    public synchronized boolean unmute(final String playerName, final String moderator) {
        PlayerModeration current = this.records.get(this.key(playerName));
        if (current == null || this.currentMute(current, System.currentTimeMillis()) == null) return false;
        List<ModerationEntry> history = new ArrayList<>(current.history());
        history.add(new ModerationEntry(System.currentTimeMillis(), ModerationAction.UNMUTE,
            moderator, "Silencio retirado manualmente"));
        this.records.put(this.key(playerName), new PlayerModeration(current.name(), null, this.trim(history)));
        this.saveData();
        return true;
    }

    public synchronized void warn(final String playerName, final String moderator, final String reason) {
        PlayerModeration record = this.record(playerName);
        List<ModerationEntry> history = new ArrayList<>(record.history());
        history.add(new ModerationEntry(System.currentTimeMillis(), ModerationAction.WARNING, moderator, reason));
        this.records.put(this.key(playerName), new PlayerModeration(playerName, record.mute(), this.trim(history)));
        this.saveData();
    }

    public synchronized MuteRecord activeMute(final String playerName) {
        PlayerModeration record = this.records.get(this.key(playerName));
        if (record == null) return null;
        MuteRecord mute = this.currentMute(record, System.currentTimeMillis());
        if (mute != null) return mute;
        if (record.mute() != null) {
            List<ModerationEntry> history = new ArrayList<>(record.history());
            history.add(new ModerationEntry(System.currentTimeMillis(), ModerationAction.UNMUTE,
                "EmsiChill", "Silencio finalizado automáticamente"));
            this.records.put(this.key(playerName), new PlayerModeration(record.name(), null, this.trim(history)));
            this.saveData();
        }
        return null;
    }

    public synchronized List<ModerationEntry> history(final String playerName) {
        PlayerModeration record = this.records.get(this.key(playerName));
        if (record == null) return Collections.emptyList();
        List<ModerationEntry> latestFirst = new ArrayList<>(record.history());
        Collections.reverse(latestFirst);
        return List.copyOf(latestFirst);
    }

    public synchronized String knownName(final String requestedName) {
        PlayerModeration record = this.records.get(this.key(requestedName));
        return record == null ? null : record.name();
    }

    public synchronized void persistData() {
        this.saveData();
    }

    private PlayerModeration record(final String playerName) {
        return this.records.getOrDefault(this.key(playerName),
            new PlayerModeration(playerName, null, List.of()));
    }

    private MuteRecord currentMute(final PlayerModeration record, final long now) {
        return record.mute() == null || record.mute().expired(now) ? null : record.mute();
    }

    private List<ModerationEntry> trim(final List<ModerationEntry> history) {
        int maximum = Math.max(10, Math.min(1_000,
            this.configFile.yaml().getInt("moderation.max-history-per-player", 100)));
        if (history.size() <= maximum) return history;
        return new ArrayList<>(history.subList(history.size() - maximum, history.size()));
    }

    private String key(final String playerName) {
        return playerName.toLowerCase(Locale.ROOT);
    }

    private void saveData() {
        this.repository.save(new LinkedHashMap<>(this.records));
    }
}
