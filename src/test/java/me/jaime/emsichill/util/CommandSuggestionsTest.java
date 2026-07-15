package me.jaime.emsichill.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

final class CommandSuggestionsTest {
    @Test
    void filtersWithoutChangingCaseAndSortsResults() {
        List<String> result = CommandSuggestions.filter(List.of("Notch", "namjoon", "Alex"), "n");

        assertEquals(List.of("Notch", "namjoon"), result);
    }

    @Test
    void doesNotMutateTheInputCollection() {
        List<String> input = List.of("home", "homes", "rtp");

        CommandSuggestions.filter(input, "home");

        assertEquals(List.of("home", "homes", "rtp"), input);
    }
}
