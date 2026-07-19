package me.jaime.emsichill.update;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

import me.jaime.emsichill.Main;

/** Orquesta la consulta y compara la Release con la versión instalada. */
public final class UpdateService {
    private static final Pattern REPOSITORY = Pattern.compile("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+");

    private final Main plugin;

    public UpdateService(final Main plugin) {
        this.plugin = plugin;
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
            if (installed.isEmpty() || latest.isEmpty()) {
                return UpdateResult.failed(current, "formato de versión no reconocido");
            }
            return latest.get().compareTo(installed.get()) > 0
                ? UpdateResult.available(current, release)
                : UpdateResult.current(current, release);
        });
    }
}
