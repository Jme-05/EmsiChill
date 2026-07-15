package me.jaime.emsichill.skin;

/** Entrada del historial con skin, instante de uso, responsable y origen de la selección. */
public record SkinHistoryEntry(String skin, long timestamp, String actor, String source) {
}