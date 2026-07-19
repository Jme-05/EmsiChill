package me.jaime.emsichill.update;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import me.jaime.emsichill.Main;

/** Orquesta la consulta y compara la Release con la versión instalada. */
public final class UpdateService {
    private static final Pattern REPOSITORY = Pattern.compile("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+");
    private static final Pattern RELEASE_TAG = Pattern.compile("[vV]?\\d+(?:\\.\\d+)*(?:-[A-Za-z0-9._-]+)?");

    private final Main plugin;
    private final UpdateInstaller installer;
    private final AtomicBoolean installing = new AtomicBoolean();

    public UpdateService(final Main plugin) {
        this.plugin = plugin;
        this.installer = new UpdateInstaller(plugin);
        this.clearResolvedPreferences();
    }

    public CompletableFuture<UpdateResult> check() {
        String current = this.plugin.getPluginMeta().getVersion();
        if (!this.plugin.settings().getBoolean("updates.enabled", true)) {
            return CompletableFuture.completedFuture(UpdateResult.failed(current, "comprobación desactivada"));
        }
        String repository = this.plugin.settings().getString("updates.repository", "Jme-05/EmsiChill").trim();
        if (!REPOSITORY.matcher(repository).matches()) {
            return CompletableFuture.completedFuture(UpdateResult.failed(current, "repositorio inválido"));
        }
        int seconds = Math.max(2, Math.min(30,
            this.plugin.settings().getInt("updates.timeout-seconds", 6)));
        GitHubReleaseClient client = new GitHubReleaseClient(repository, "EmsiChill/" + current,
            Duration.ofSeconds(seconds));
        return client.latestRelease().handle((release, failure) -> {
            if (failure != null) return UpdateResult.failed(current, failure.getMessage());
            var installed = VersionNumber.parse(current);
            var latest = VersionNumber.parse(release.tag());
            if (installed.isEmpty() || latest.isEmpty() || !RELEASE_TAG.matcher(release.tag()).matches()) {
                return UpdateResult.failed(current, "formato de versión no reconocido");
            }
            return latest.get().compareTo(installed.get()) > 0
                ? UpdateResult.available(current, release)
                : UpdateResult.current(current, release);
        });
    }

    public CompletableFuture<UpdateInstallResult> install(final String requestedVersion) {
        if (!this.installing.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(UpdateInstallResult.of(
                UpdateInstallResult.Status.IN_PROGRESS, requestedVersion, "ya existe una descarga en curso"));
        }
        CompletableFuture<UpdateInstallResult> operation = this.check().thenCompose(result -> {
            if (result.status() == UpdateResult.Status.FAILED) {
                return CompletableFuture.completedFuture(UpdateInstallResult.of(
                    UpdateInstallResult.Status.FAILED, requestedVersion, result.error()));
            }
            if (result.status() == UpdateResult.Status.UP_TO_DATE) {
                return CompletableFuture.completedFuture(UpdateInstallResult.of(
                    UpdateInstallResult.Status.NO_UPDATE, requestedVersion, "no hay una actualización disponible"));
            }
            if (!sameVersion(requestedVersion, result.release().tag())) {
                return CompletableFuture.completedFuture(UpdateInstallResult.of(
                    UpdateInstallResult.Status.VERSION_CHANGED, result.release().tag(),
                    "la versión más reciente cambió"));
            }
            return this.installer.prepare(result.release());
        });
        return operation.whenComplete((result, failure) -> this.installing.set(false));
    }

    public boolean ignore(final String version) {
        if (!validVersion(version)) return false;
        this.plugin.settings().set("updates.ignored-version", version);
        return this.plugin.saveSettings();
    }

    public void markStaged(final String version) {
        this.plugin.settings().set("updates.staged-version", version);
        this.plugin.settings().set("updates.ignored-version", "");
        this.plugin.saveSettings();
    }

    public boolean isSuppressed(final String version) {
        return sameVersion(version, this.plugin.settings().getString("updates.ignored-version", ""))
            || sameVersion(version, this.plugin.settings().getString("updates.staged-version", ""));
    }

    private void clearResolvedPreferences() {
        String current = this.plugin.getPluginMeta().getVersion();
        boolean changed = this.clearIfResolved("updates.ignored-version", current);
        changed |= this.clearIfResolved("updates.staged-version", current);
        if (changed) this.plugin.saveSettings();
    }

    private boolean clearIfResolved(final String key, final String current) {
        String stored = this.plugin.settings().getString(key, "");
        var storedVersion = VersionNumber.parse(stored);
        var currentVersion = VersionNumber.parse(current);
        if (storedVersion.isEmpty() || currentVersion.isEmpty()
            || storedVersion.get().compareTo(currentVersion.get()) > 0) return false;
        this.plugin.settings().set(key, "");
        return !stored.isBlank();
    }

    private static boolean validVersion(final String version) {
        return version != null && RELEASE_TAG.matcher(version).matches() && VersionNumber.parse(version).isPresent();
    }

    private static boolean sameVersion(final String first, final String second) {
        var left = VersionNumber.parse(first);
        var right = VersionNumber.parse(second);
        return left.isPresent() && right.isPresent() && left.get().compareTo(right.get()) == 0;
    }
}
