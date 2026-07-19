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

    @Test
    void selectsExactVersionedJarAsset() {
        String digest = "sha256:" + "a".repeat(64);
        String json = """
            {"tag_name":"v5.1.2","assets":[
              {"name":"EmsiChill-5.1.2-jar-with-dependencies.jar","size":400,
               "digest":"%s","browser_download_url":"https://github.com/wrong"},
              {"name":"EmsiChill-5.1.2.jar","size":300,
               "digest":"%s","browser_download_url":"https://github.com/correct"}
            ]}
            """.formatted(digest, digest);

        ReleaseAsset asset = GitHubReleaseClient.readReleaseAsset(json, "v5.1.2");

        assertEquals("EmsiChill-5.1.2.jar", asset.name());
        assertEquals(300L, asset.size());
        assertEquals("https://github.com/correct", asset.downloadUrl());
    }
}
