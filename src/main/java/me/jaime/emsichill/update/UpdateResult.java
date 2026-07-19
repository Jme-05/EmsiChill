package me.jaime.emsichill.update;

/** Resultado estable que desacopla la consulta HTTP de los mensajes del comando. */
public record UpdateResult(Status status, String currentVersion, ReleaseInfo release, String error) {
    public enum Status {
        UPDATE_AVAILABLE,
        UP_TO_DATE,
        FAILED
    }

    static UpdateResult available(final String currentVersion, final ReleaseInfo release) {
        return new UpdateResult(Status.UPDATE_AVAILABLE, currentVersion, release, null);
    }

    static UpdateResult current(final String currentVersion, final ReleaseInfo release) {
        return new UpdateResult(Status.UP_TO_DATE, currentVersion, release, null);
    }

    static UpdateResult failed(final String currentVersion, final String error) {
        return new UpdateResult(Status.FAILED, currentVersion, null, error);
    }
}
