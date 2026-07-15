package me.jaime.emsichill.playerinfo;

/** Resumen persistente de actividad de un jugador. */
record PlayerRecord(String name, long firstSeen, long lastSeen, long totalMillis) {
}