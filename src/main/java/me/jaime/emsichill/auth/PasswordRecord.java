package me.jaime.emsichill.auth;

/** Datos persistentes necesarios para verificar una contraseña sin guardar el texto original. */
public record PasswordRecord(String name, int iterations, String salt, String hash) {
}