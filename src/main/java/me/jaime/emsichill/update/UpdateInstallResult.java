package me.jaime.emsichill.update;

import java.nio.file.Path;

/** Resultado de preparar una actualización para el siguiente reinicio. */
public record UpdateInstallResult(Status status, String version, Path file, String error) {
    public enum Status {
        PREPARED,
        NO_UPDATE,
        VERSION_CHANGED,
        IN_PROGRESS,
        DISABLED,
        FAILED
    }

    static UpdateInstallResult prepared(final String version, final Path file) {
        return new UpdateInstallResult(Status.PREPARED, version, file, null);
    }

    static UpdateInstallResult of(final Status status, final String version, final String error) {
        return new UpdateInstallResult(status, version, null, error);
    }
}
