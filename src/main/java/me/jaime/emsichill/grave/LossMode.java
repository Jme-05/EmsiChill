package me.jaime.emsichill.grave;

import java.util.Locale;

/** Resultado configurado para el inventario al morir. */
enum LossMode {
    GRAVE,
    KEEP,
    DROP;

    static LossMode parse(final String value) {
        if (value == null) {
            return null;
        }
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}