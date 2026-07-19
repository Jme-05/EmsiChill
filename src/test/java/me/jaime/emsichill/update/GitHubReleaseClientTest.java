package me.jaime.emsichill.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class GitHubReleaseClientTest {
    @Test
    void readsEscapedJsonStrings() {
        String json = "{\"tag_name\":\"v5.1.0\",\"html_url\":\"https:\\/\\/github.com\\/Jme-05\\/EmsiChill\"}";

        assertEquals("v5.1.0", GitHubReleaseClient.readStringField(json, "tag_name"));
        assertEquals("https://github.com/Jme-05/EmsiChill",
            GitHubReleaseClient.readStringField(json, "html_url"));
    }

    @Test
    void returnsNullForMissingFields() {
        assertNull(GitHubReleaseClient.readStringField("{}", "tag_name"));
    }
}
