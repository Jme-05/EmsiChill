package me.jaime.emsichill.skin;

/** Textura firmada que Paper puede insertar de forma segura en un perfil de jugador. */
public record SkinTexture(String name, String value, String signature, long fetchedAt) {
}