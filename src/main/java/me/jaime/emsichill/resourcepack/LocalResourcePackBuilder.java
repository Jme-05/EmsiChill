package me.jaime.emsichill.resourcepack;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Builds deterministic client packs from local ZIP files and unpacked pack directories. */
final class LocalResourcePackBuilder {
    private static final String GENERATED_DIRECTORY = ".generated";
    private static final int COPY_BUFFER_SIZE = 64 * 1024;

    private LocalResourcePackBuilder() {
    }

    static List<BuildResult> build(final Path resourcePacksDirectory, final long maximumUncompressedBytes)
        throws IOException {
        Files.createDirectories(resourcePacksDirectory);
        Path generatedDirectory = resourcePacksDirectory.resolve(GENERATED_DIRECTORY);
        Files.createDirectories(generatedDirectory);
        CopyBudget budget = new CopyBudget(Math.max(1L, maximumUncompressedBytes));
        List<BuildResult> results = new ArrayList<>();
        for (Path source : discoverSources(resourcePacksDirectory)) {
            results.add(buildSource(source, generatedDirectory, budget));
        }
        return List.copyOf(results);
    }

    private static BuildResult buildSource(final Path source, final Path generatedDirectory,
                                           final CopyBudget budget) throws IOException {
        Path workDirectory = Files.createTempDirectory(generatedDirectory, "build-");
        String sourceName = source.getFileName().toString();
        try {
            Path packDirectory = workDirectory.resolve("pack");
            Files.createDirectories(packDirectory);
            boolean copied = Files.isDirectory(source)
                ? copyDirectoryPack(source, packDirectory, budget)
                : copyZipPack(source, packDirectory, budget);
            if (!copied || !Files.isRegularFile(packDirectory.resolve("pack.mcmeta"))) {
                throw new IOException(sourceName + " does not contain pack.mcmeta at the pack root");
            }

            Path temporaryZip = workDirectory.resolve("pack.zip");
            zipDirectory(packDirectory, temporaryZip);
            byte[] sha1 = digest(temporaryZip, "SHA-1");
            String sha1Hex = HexFormat.of().formatHex(sha1);
            Path output = generatedDirectory.resolve("pack-" + sha1Hex + ".zip");
            if (Files.exists(output)) Files.delete(temporaryZip);
            else moveAtomically(temporaryZip, output);
            return new BuildResult(sourceName, output, sha1Hex, sha1, Files.size(output));
        } finally {
            deleteGeneratedTree(workDirectory, generatedDirectory);
        }
    }

    private static List<Path> discoverSources(final Path directory) throws IOException {
        try (Stream<Path> stream = Files.list(directory)) {
            return stream
                .filter(path -> !path.getFileName().toString().equalsIgnoreCase(GENERATED_DIRECTORY))
                .filter(path -> Files.isDirectory(path) || isZip(path))
                .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                .toList();
        }
    }

    private static boolean isZip(final Path path) {
        return Files.isRegularFile(path)
            && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip");
    }

    private static boolean copyDirectoryPack(final Path source, final Path destination, final CopyBudget budget)
        throws IOException {
        Path root = directoryPackRoot(source);
        if (root == null) return false;
        try (Stream<Path> stream = Files.walk(root)) {
            for (Path file : stream.filter(Files::isRegularFile).sorted().toList()) {
                if (Files.isSymbolicLink(file)) continue;
                Path relative = root.relativize(file);
                Path target = safeDestination(destination, relative.toString().replace('\\', '/'));
                if (target == null) throw new IOException("unsafe path in local resource pack: " + relative);
                Files.createDirectories(target.getParent());
                try (InputStream input = Files.newInputStream(file);
                     OutputStream output = Files.newOutputStream(target)) {
                    copyLimited(input, output, budget);
                }
            }
        }
        return true;
    }

    private static Path directoryPackRoot(final Path source) throws IOException {
        if (Files.isRegularFile(source.resolve("pack.mcmeta"))) return source;
        try (Stream<Path> stream = Files.list(source)) {
            List<Path> candidates = stream.filter(Files::isDirectory)
                .filter(path -> Files.isRegularFile(path.resolve("pack.mcmeta")))
                .toList();
            return candidates.size() == 1 ? candidates.getFirst() : null;
        }
    }

    private static boolean copyZipPack(final Path source, final Path destination, final CopyBudget budget)
        throws IOException {
        try (ZipFile zip = new ZipFile(source.toFile())) {
            String prefix = zipPackPrefix(zip);
            if (prefix == null) return false;
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String normalized = normalizeEntry(entry.getName());
                if (normalized == null || !normalized.startsWith(prefix)) continue;
                String relative = normalized.substring(prefix.length());
                if (relative.isBlank()) continue;
                Path target = safeDestination(destination, relative);
                if (target == null) throw new IOException("unsafe path in resource pack ZIP: " + entry.getName());
                Files.createDirectories(target.getParent());
                try (InputStream input = zip.getInputStream(entry);
                     OutputStream output = Files.newOutputStream(target)) {
                    copyLimited(input, output, budget);
                }
            }
            return true;
        }
    }

    private static String zipPackPrefix(final ZipFile zip) {
        Set<String> prefixes = new LinkedHashSet<>();
        var entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.isDirectory()) continue;
            String normalized = normalizeEntry(entry.getName());
            if (normalized == null) continue;
            if (normalized.equals("pack.mcmeta")) return "";
            if (normalized.endsWith("/pack.mcmeta")) {
                prefixes.add(normalized.substring(0, normalized.length() - "pack.mcmeta".length()));
            }
        }
        return prefixes.size() == 1 ? prefixes.iterator().next() : null;
    }

    private static String normalizeEntry(final String value) {
        if (value == null) return null;
        String[] segments = value.replace('\\', '/').split("/");
        List<String> safe = new ArrayList<>();
        for (String segment : segments) {
            if (segment.isBlank() || segment.equals(".")) continue;
            if (segment.equals("..")) return null;
            safe.add(segment);
        }
        return String.join("/", safe);
    }

    private static Path safeDestination(final Path root, final String relative) {
        String normalized = normalizeEntry(relative);
        if (normalized == null || normalized.isBlank()) return null;
        Path destination = root.resolve(normalized).normalize();
        return destination.startsWith(root.normalize()) ? destination : null;
    }

    private static void copyLimited(final InputStream input, final OutputStream output, final CopyBudget budget)
        throws IOException {
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read == 0) continue;
            budget.add(read);
            output.write(buffer, 0, read);
        }
    }

    private static void zipDirectory(final Path source, final Path target) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target));
             Stream<Path> stream = Files.walk(source)) {
            for (Path file : stream.filter(Files::isRegularFile).sorted().toList()) {
                String name = source.relativize(file).toString().replace('\\', '/');
                ZipEntry entry = new ZipEntry(name);
                entry.setTime(0L);
                zip.putNextEntry(entry);
                Files.copy(file, zip);
                zip.closeEntry();
            }
        }
    }

    private static byte[] digest(final Path file, final String algorithm) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException(algorithm + " is not available", exception);
        }
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[COPY_BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) digest.update(buffer, 0, read);
            }
        }
        return digest.digest();
    }

    private static void moveAtomically(final Path source, final Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination);
        }
    }

    private static void deleteGeneratedTree(final Path target, final Path generatedRoot) throws IOException {
        Path normalizedTarget = target.toAbsolutePath().normalize();
        Path normalizedRoot = generatedRoot.toAbsolutePath().normalize();
        if (!normalizedTarget.startsWith(normalizedRoot) || normalizedTarget.equals(normalizedRoot)) return;
        if (!Files.exists(normalizedTarget)) return;
        try (Stream<Path> stream = Files.walk(normalizedTarget)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    record BuildResult(String sourceName, Path file, String sha1Hex, byte[] sha1, long size) {
        BuildResult {
            sha1 = sha1.clone();
        }

        @Override
        public byte[] sha1() {
            return this.sha1.clone();
        }
    }

    private static final class CopyBudget {
        private final long maximum;
        private long copied;

        private CopyBudget(final long maximum) {
            this.maximum = maximum;
        }

        private void add(final long amount) throws IOException {
            this.copied += amount;
            if (this.copied > this.maximum) {
                throw new IOException("resource packs exceed the configured uncompressed size limit");
            }
        }
    }
}
