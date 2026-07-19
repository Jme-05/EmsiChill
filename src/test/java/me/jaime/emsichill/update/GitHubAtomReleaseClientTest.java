package me.jaime.emsichill.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GitHubAtomReleaseClientTest {
    @Test
    void readsLatestReleaseAndBuildsExpectedAssetUrl() {
        String feed = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <entry>
                <link rel="alternate" href="https://github.com/Jme-05/EmsiChill/releases/tag/5.1.4"/>
                <title>EmsiChill 5.1.4</title>
                <content type="html">&lt;h2&gt;Cambios&lt;/h2&gt;&lt;ul&gt;&lt;li&gt;Respaldo por feed&lt;/li&gt;&lt;/ul&gt;</content>
              </entry>
            </feed>
            """;

        ReleaseInfo release = GitHubAtomReleaseClient.parse(feed, "Jme-05/EmsiChill");

        assertEquals("5.1.4", release.tag());
        assertTrue(release.notes().contains("Respaldo por feed"));
        assertEquals("EmsiChill-5.1.4.jar", release.asset().name());
        assertEquals("https://github.com/Jme-05/EmsiChill/releases/download/5.1.4/EmsiChill-5.1.4.jar",
            release.asset().downloadUrl());
        assertFalse(release.asset().hasVerifiedMetadata());
    }
}
