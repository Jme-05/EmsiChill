package me.jaime.emsichill.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Filtra y ordena sugerencias sin modificar la colección original del módulo que las entrega. */
public final class CommandSuggestions {
    private CommandSuggestions() {
    }

    public static List<String> filter(final Collection<String> values, final String prefix) {
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lowerPrefix)) {
                matches.add(value);
            }
        }
        Collections.sort(matches);
        return matches;
    }
}