package me.jaime.emsichill.staff;

/** Silencio activo; expiresAt igual a cero representa una duracion permanente. */
record MuteRecord(long createdAt, long expiresAt, String moderator, String reason) {
    boolean permanent() {
        return this.expiresAt == 0L;
    }

    boolean expired(final long now) {
        return !this.permanent() && this.expiresAt <= now;
    }
}
