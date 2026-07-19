package me.jaime.emsichill.staff;

/** Entrada inmutable del historial de sanciones de un jugador. */
record ModerationEntry(long createdAt, ModerationAction action, String moderator, String reason) {
}
