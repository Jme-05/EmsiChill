package me.jaime.emsichill.staff;

import java.time.Duration;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Convierte duraciones breves como 30s, 10m, 2h o 1d a milisegundos. */
final class MuteDuration {
    private static final Pattern FORMAT = Pattern.compile("^(\\d+)([smhd]?)$", Pattern.CASE_INSENSITIVE);
    private static final long MAX_MILLIS = Duration.ofDays(365).toMillis();

    private MuteDuration() {
    }

    static OptionalLong parse(final String input) {
        if (input == null) return OptionalLong.empty();
        Matcher matcher = FORMAT.matcher(input.trim().toLowerCase(Locale.ROOT));
        if (!matcher.matches()) return OptionalLong.empty();
        try {
            long amount = Long.parseLong(matcher.group(1));
            long multiplier = switch (matcher.group(2)) {
                case "m" -> Duration.ofMinutes(1).toMillis();
                case "h" -> Duration.ofHours(1).toMillis();
                case "d" -> Duration.ofDays(1).toMillis();
                default -> Duration.ofSeconds(1).toMillis();
            };
            long millis = Math.multiplyExact(amount, multiplier);
            return millis >= 1_000L && millis <= MAX_MILLIS
                ? OptionalLong.of(millis)
                : OptionalLong.empty();
        } catch (ArithmeticException | NumberFormatException exception) {
            return OptionalLong.empty();
        }
    }

    static String format(final long millis) {
        Duration duration = Duration.ofMillis(Math.max(0L, millis));
        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m " + seconds + "s";
        return seconds + "s";
    }
}
