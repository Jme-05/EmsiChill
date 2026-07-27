package me.jaime.emsichill.update;

import java.nio.file.Path;

/** Result of downloading a Paper server jar for manual restart/update. */
public record PaperUpdateInstallResult(Status status, String target, Path file, String error) {
    public enum Status {
        PREPARED,
        NO_UPDATE,
        VERSION_CHANGED,
        IN_PROGRESS,
        DISABLED,
        FAILED
    }

    static PaperUpdateInstallResult prepared(final PaperBuildInfo build, final Path file) {
        return new PaperUpdateInstallResult(Status.PREPARED, build.identifier(), file, null);
    }

    static PaperUpdateInstallResult of(final Status status, final String target, final String error) {
        return new PaperUpdateInstallResult(status, target, null, error);
    }
}
