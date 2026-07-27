package me.jaime.emsichill.update;

/** Result of comparing the current server jar with the latest PaperMC build. */
public record PaperUpdateResult(Status status, PaperServerVersion current, PaperBuildInfo latest, String error) {
    public enum Status {
        UPDATE_AVAILABLE,
        UP_TO_DATE,
        FAILED
    }

    static PaperUpdateResult available(final PaperServerVersion current, final PaperBuildInfo latest) {
        return new PaperUpdateResult(Status.UPDATE_AVAILABLE, current, latest, null);
    }

    static PaperUpdateResult current(final PaperServerVersion current, final PaperBuildInfo latest) {
        return new PaperUpdateResult(Status.UP_TO_DATE, current, latest, null);
    }

    static PaperUpdateResult failed(final PaperServerVersion current, final String error) {
        return new PaperUpdateResult(Status.FAILED, current, null, error);
    }
}
