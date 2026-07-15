package me.jaime.emsichill.auth;

/** Identificador protegido y vencimiento utilizados para restaurar una sesión válida. */
public record SessionRecord(String addressHash, long expiresAt) {
}