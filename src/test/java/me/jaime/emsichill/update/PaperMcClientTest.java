package me.jaime.emsichill.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

class PaperMcClientTest {
    @Test
    void readsVersionsInPublishedOrderAndSkipsPrereleasesByDefault() {
        String json = """
            {"project":{"id":"paper"},"versions":{
              "26.3":["26.3-rc-1"],
              "26.2":["26.2","26.2-rc-2"],
              "1.21":["1.21.11","1.21.10"]
            }}
            """;

        assertEquals(List.of("26.2", "1.21.11", "1.21.10"),
            PaperMcClient.readVersions(json, false));
    }

    @Test
    void canIncludePrereleaseVersionsWhenExperimentalBuildsAreAllowed() {
        String json = """
            {"versions":{"26.3":["26.3-rc-1"],"26.2":["26.2"]}}
            """;

        assertEquals(List.of("26.3-rc-1", "26.2"),
            PaperMcClient.readVersions(json, true));
    }

    @Test
    void selectsStableBuildWithVerifiedDownload() {
        String json = """
            [
              {"id":85,"channel":"BETA","downloads":{"server:default":{
                "name":"paper-26.2-85.jar","checksums":{"sha256":"%s"},"size":500,
                "url":"https://fill-data.papermc.io/v1/objects/beta/paper-26.2-85.jar"}}},
              {"id":84,"channel":"STABLE","downloads":{"server:default":{
                "name":"paper-26.2-84.jar","checksums":{"sha256":"%s"},"size":400,
                "url":"https://fill-data.papermc.io/v1/objects/stable/paper-26.2-84.jar"}}}
            ]
            """.formatted("a".repeat(64), "b".repeat(64));

        PaperBuildInfo build = PaperMcClient.readBuild("paper", "26.2", json, false);

        assertEquals("26.2", build.minecraftVersion());
        assertEquals(84, build.build());
        assertEquals("STABLE", build.channel());
        assertEquals("paper-26.2-84.jar", build.download().name());
    }

    @Test
    void returnsNullWhenOnlyBetaBuildsAreAvailableAndExperimentalIsDisabled() {
        String json = """
            [{"id":85,"channel":"BETA","downloads":{"server:default":{
              "name":"paper-26.2-85.jar","checksums":{"sha256":"%s"},"size":500,
              "url":"https://fill-data.papermc.io/v1/objects/beta/paper-26.2-85.jar"}}}]
            """.formatted("a".repeat(64));

        assertNull(PaperMcClient.readBuild("paper", "26.2", json, false));
    }
}
