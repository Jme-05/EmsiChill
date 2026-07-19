package me.jaime.emsichill.update;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UpdateInstallerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void acceptsMatchingEmsiChillJar() throws Exception {
        Path jar = this.createJar("5.1.2");

        assertDoesNotThrow(() -> UpdateInstaller.validateJar(jar, "v5.1.2"));
    }

    @Test
    void rejectsJarWithDifferentVersion() throws Exception {
        Path jar = this.createJar("5.1.1");

        assertThrows(java.io.IOException.class, () -> UpdateInstaller.validateJar(jar, "5.1.2"));
    }

    private Path createJar(final String version) throws Exception {
        Path jar = this.temporaryDirectory.resolve("EmsiChill-" + version + ".jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry("plugin.yml"));
            output.write(("name: EmsiChill\nversion: '" + version
                + "'\nmain: me.jaime.emsichill.Main\napi-version: '26.2'\n")
                .getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new JarEntry("me/jaime/emsichill/Main.class"));
            output.write(new byte[] {0});
            output.closeEntry();
        }
        return jar;
    }
}
