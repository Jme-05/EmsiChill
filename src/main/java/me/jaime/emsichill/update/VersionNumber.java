package me.jaime.emsichill.update;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Compara etiquetas numéricas como v5.1.0 sin depender de librerías externas. */
record VersionNumber(List<Integer> parts) implements Comparable<VersionNumber> {
    static Optional<VersionNumber> parse(final String value) {
        if (value == null) return Optional.empty();
        String normalized = value.trim();
        if (normalized.startsWith("v") || normalized.startsWith("V")) normalized = normalized.substring(1);
        int suffix = normalized.indexOf('-');
        if (suffix >= 0) normalized = normalized.substring(0, suffix);
        if (normalized.isBlank()) return Optional.empty();

        List<Integer> parts = new ArrayList<>();
        for (String part : normalized.split("\\.")) {
            if (part.isBlank() || !part.chars().allMatch(Character::isDigit)) return Optional.empty();
            try {
                parts.add(Integer.parseInt(part));
            } catch (NumberFormatException exception) {
                return Optional.empty();
            }
        }
        return Optional.of(new VersionNumber(List.copyOf(parts)));
    }

    @Override
    public int compareTo(final VersionNumber other) {
        int length = Math.max(this.parts.size(), other.parts.size());
        for (int index = 0; index < length; index++) {
            int left = index < this.parts.size() ? this.parts.get(index) : 0;
            int right = index < other.parts.size() ? other.parts.get(index) : 0;
            int comparison = Integer.compare(left, right);
            if (comparison != 0) return comparison;
        }
        return 0;
    }
}
