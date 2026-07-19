package me.jaime.emsichill.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class ReleaseNotesFormatterTest {
    @Test
    void convertsCommonMarkdownToPlainChatLines() {
        List<String> lines = ReleaseNotesFormatter.format("""
            ## Version 5.1.3
            - Added **moderation** commands.
            - Read the [documentation](https://example.com).
            """);

        assertEquals(List.of("Version 5.1.3", "Added moderation commands.", "Read the documentation."), lines);
    }

    @Test
    void limitsTheNumberAndLengthOfLines() {
        String markdown = ("- " + "x".repeat(220) + "\n").repeat(20);
        List<String> lines = ReleaseNotesFormatter.format(markdown);

        assertEquals(12, lines.size());
        assertTrue(lines.getFirst().length() <= 160);
        assertTrue(lines.getLast().endsWith(" ..."));
    }
}
