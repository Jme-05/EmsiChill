package me.jaime.emsichill.update;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;

import me.jaime.emsichill.Main;

/** Checks and prepares Paper server updates from PaperMC's downloads service. */
final class PaperUpdateService {
    private static final Pattern PROJECT = Pattern.compile("[a-z0-9-]+");
    private static final Pattern TARGET = Pattern.compile("([0-9]+(?:\\.[0-9]+)*(?:-[A-Za-z0-9._-]+)?)#(\\d+)");

    private final Main plugin;
    private final PaperUpdateInstaller installer;
    private final AtomicBoolean downloading = new AtomicBoolean();

    PaperUpdateService(final Main plugin) {
        this.plugin = plugin;
        this.installer = new PaperUpdateInstaller(plugin);
        this.clearResolvedPreferences();
    }

    CompletableFuture<PaperUpdateResult> check() {
        PaperServerVersion current = this.currentServer();
        if (!this.plugin.settings().getBoolean("updates.paper.enabled", true)) {
            return CompletableFuture.completedFuture(PaperUpdateResult.failed(current, "Paper checks are disabled"));
        }
        String project = this.project();
        if (!PROJECT.matcher(project).matches()) {
            return CompletableFuture.completedFuture(PaperUpdateResult.failed(current, "invalid PaperMC project"));
        }
        int seconds = Math.max(2, Math.min(30,
            this.plugin.settings().getInt("updates.paper.timeout-seconds", 8)));
        boolean includeExperimental = this.plugin.settings()
            .getBoolean("updates.paper.include-experimental-builds", false);
        int maxVersionSearch = Math.max(1, Math.min(50,
            this.plugin.settings().getInt("updates.paper.max-version-search", 12)));
        PaperMcClient client = new PaperMcClient(project, this.userAgent(), Duration.ofSeconds(seconds),
            includeExperimental, maxVersionSearch);
        return client.latestBuild().handle((latest, failure) -> {
            if (failure != null) return PaperUpdateResult.failed(current, rootMessage(failure));
            return this.isNewerThanCurrent(current, latest)
                ? PaperUpdateResult.available(current, latest)
                : PaperUpdateResult.current(current, latest);
        });
    }

    CompletableFuture<PaperUpdateInstallResult> download(final String requestedVersion, final int requestedBuild) {
        String requestedTarget = target(requestedVersion, requestedBuild);
        if (!this.downloading.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(PaperUpdateInstallResult.of(
                PaperUpdateInstallResult.Status.IN_PROGRESS, requestedTarget, "a Paper download is already running"));
        }
        CompletableFuture<PaperUpdateInstallResult> operation = this.check().thenCompose(result -> {
            if (result.status() == PaperUpdateResult.Status.FAILED) {
                return CompletableFuture.completedFuture(PaperUpdateInstallResult.of(
                    PaperUpdateInstallResult.Status.FAILED, requestedTarget, result.error()));
            }
            if (result.status() == PaperUpdateResult.Status.UP_TO_DATE) {
                return CompletableFuture.completedFuture(PaperUpdateInstallResult.of(
                    PaperUpdateInstallResult.Status.NO_UPDATE, requestedTarget, "Paper is already current"));
            }
            if (!this.matchesBuild(requestedVersion, requestedBuild, result.latest())) {
                return CompletableFuture.completedFuture(PaperUpdateInstallResult.of(
                    PaperUpdateInstallResult.Status.VERSION_CHANGED, result.latest().identifier(),
                    "the latest Paper build changed"));
            }
            return this.installer.prepare(result.latest());
        });
        return operation.whenComplete((result, failure) -> this.downloading.set(false));
    }

    boolean ignore(final String version, final int build) {
        if (!validTarget(version, build)) return false;
        this.plugin.settings().set("updates.paper.ignored-build", target(version, build));
        return this.plugin.saveSettings();
    }

    void markStaged(final String target) {
        this.plugin.settings().set("updates.paper.staged-build", target);
        this.plugin.settings().set("updates.paper.ignored-build", "");
        this.plugin.saveSettings();
    }

    boolean isSuppressed(final PaperBuildInfo build) {
        return sameTarget(build.identifier(), this.plugin.settings().getString("updates.paper.ignored-build", ""))
            || sameTarget(build.identifier(), this.plugin.settings().getString("updates.paper.staged-build", ""));
    }

    boolean matchesBuild(final String version, final int build, final PaperBuildInfo latest) {
        return latest != null && build == latest.build() && sameVersion(version, latest.minecraftVersion());
    }

    private boolean isNewerThanCurrent(final PaperServerVersion current, final PaperBuildInfo latest) {
        Optional<VersionNumber> currentVersion = VersionNumber.parse(current.minecraftVersion());
        Optional<VersionNumber> latestVersion = VersionNumber.parse(latest.minecraftVersion());
        if (currentVersion.isEmpty() || latestVersion.isEmpty()) return true;
        int versionComparison = latestVersion.get().compareTo(currentVersion.get());
        if (versionComparison != 0) return versionComparison > 0;
        return current.build().isEmpty() || latest.build() > current.build().getAsInt();
    }

    private PaperServerVersion currentServer() {
        return PaperServerVersion.detect(Bukkit.getMinecraftVersion(), this.plugin.getServer().getVersion());
    }

    private String project() {
        return this.plugin.settings().getString("updates.paper.project", "paper").trim().toLowerCase();
    }

    private String userAgent() {
        return this.plugin.settings().getString("updates.paper.user-agent",
                "EmsiChill/${version} (https://github.com/Jme-05/EmsiChill)")
            .replace("${version}", this.plugin.getPluginMeta().getVersion());
    }

    private void clearResolvedPreferences() {
        PaperServerVersion current = this.currentServer();
        boolean changed = this.clearIfResolved("updates.paper.ignored-build", current);
        changed |= this.clearIfResolved("updates.paper.staged-build", current);
        if (changed) this.plugin.saveSettings();
    }

    private boolean clearIfResolved(final String key, final PaperServerVersion current) {
        String stored = this.plugin.settings().getString(key, "");
        if (!targetResolved(stored, current)) return false;
        this.plugin.settings().set(key, "");
        return !stored.isBlank();
    }

    private static boolean targetResolved(final String target, final PaperServerVersion current) {
        ParsedTarget parsed = parseTarget(target);
        if (parsed == null || current.build().isEmpty()) return false;
        Optional<VersionNumber> targetVersion = VersionNumber.parse(parsed.version());
        Optional<VersionNumber> currentVersion = VersionNumber.parse(current.minecraftVersion());
        if (targetVersion.isEmpty() || currentVersion.isEmpty()) return false;
        int comparison = currentVersion.get().compareTo(targetVersion.get());
        return comparison > 0 || comparison == 0 && current.build().getAsInt() >= parsed.build();
    }

    private static boolean validTarget(final String version, final int build) {
        return build > 0 && VersionNumber.parse(version).isPresent();
    }

    private static boolean sameTarget(final String first, final String second) {
        ParsedTarget left = parseTarget(first);
        ParsedTarget right = parseTarget(second);
        return left != null && right != null && left.build() == right.build()
            && sameVersion(left.version(), right.version());
    }

    private static boolean sameVersion(final String first, final String second) {
        Optional<VersionNumber> left = VersionNumber.parse(first);
        Optional<VersionNumber> right = VersionNumber.parse(second);
        return left.isPresent() && right.isPresent() && left.get().compareTo(right.get()) == 0;
    }

    static String target(final String version, final int build) {
        return version + "#" + build;
    }

    private static ParsedTarget parseTarget(final String value) {
        Matcher matcher = TARGET.matcher(value == null ? "" : value.trim());
        if (!matcher.matches()) return null;
        try {
            return new ParsedTarget(matcher.group(1), Integer.parseInt(matcher.group(2)));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String rootMessage(final Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private record ParsedTarget(String version, int build) {
    }
}
