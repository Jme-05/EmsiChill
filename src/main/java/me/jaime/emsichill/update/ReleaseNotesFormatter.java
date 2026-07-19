package me.jaime.emsichill.update;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Convierte Markdown de GitHub en un resumen corto y legible dentro del chat. */
public final class ReleaseNotesFormatter {
    private static final int MAX_LINES = 12;
    private static final int MAX_LINE_LENGTH = 160;
    private static final Pattern LINK = Pattern.compile("\\[([^]]+)]\\([^)]+\\)");

    private ReleaseNotesFormatter() {
    }

    public static List<String> format(final String markdown) {
        if (markdown == null || markdown.isBlank()) return List.of();
        List<String> lines = new ArrayList<>();
        boolean truncated = false;
        for (String source : markdown.replace("\r", "").split("\n")) {
            String line = clean(source);
            if (line.isBlank()) continue;
            if (lines.size() == MAX_LINES) {
                truncated = true;
                break;
            }
            if (line.length() > MAX_LINE_LENGTH) {
                line = line.substring(0, MAX_LINE_LENGTH - 3).stripTrailing() + "...";
            }
            lines.add(line);
        }
        if (truncated && !lines.isEmpty()) {
            String last = lines.getLast();
            int contentLimit = MAX_LINE_LENGTH - 4;
            if (last.length() > contentLimit) last = last.substring(0, contentLimit).stripTrailing();
            lines.set(lines.size() - 1, last + " ...");
        }
        return List.copyOf(lines);
    }

    private static String clean(final String source) {
        String line = source.strip()
            .replaceFirst("^#{1,6}\\s*", "")
            .replaceFirst("^[-*+]\\s+", "")
            .replaceFirst("^\\d+[.)]\\s+", "")
            .replaceFirst("^>\\s*", "")
            .replaceFirst("^\\[[ xX]]\\s*", "")
            .replace("**", "")
            .replace("__", "")
            .replace("`", "");
        Matcher links = LINK.matcher(line);
        return links.replaceAll("$1").strip();
    }
}
