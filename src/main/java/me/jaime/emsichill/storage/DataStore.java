package me.jaime.emsichill.storage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Serializa escrituras por archivo, permite guardados asíncronos y espera operaciones pendientes
 * durante el apagado del plugin.
 */
public final class DataStore implements AutoCloseable {
    private final Logger logger;
    private final ExecutorService writer;
    private final Map<Path, PendingWrite> pending = new LinkedHashMap<>();
    private final AtomicBoolean draining = new AtomicBoolean();

    public DataStore(final Logger logger) {
        this.logger = logger;
        this.writer = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "EmsiChill-DataWriter");
            thread.setDaemon(true);
            return thread;
        });
    }

    public YamlConfiguration load(final File file) {
        return AtomicYamlStorage.load(file, this.logger);
    }

    public void saveAsync(final File file, final YamlConfiguration yaml) {
        PendingWrite write = new PendingWrite(file, AtomicYamlStorage.serialize(yaml));
        synchronized (this.pending) {
            // Varias solicitudes para el mismo archivo se condensan en la versión más reciente.
            this.pending.put(file.toPath().toAbsolutePath().normalize(), write);
        }
        this.scheduleDrain();
    }

    public boolean saveNow(final File file, final YamlConfiguration yaml) {
        Path path = file.toPath().toAbsolutePath().normalize();
        synchronized (this.pending) {
            // Retira la versión aplazada para impedir que sobrescriba este guardado inmediato.
            this.pending.remove(path);
        }
        PendingWrite write = new PendingWrite(file, AtomicYamlStorage.serialize(yaml));
        try {
            return this.writer.submit(() -> this.write(write)).get(15, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | java.util.concurrent.TimeoutException exception) {
            this.logger.warning("No se pudo completar el guardado de " + file.getAbsolutePath() + ": " + exception.getMessage());
            return false;
        } catch (RejectedExecutionException exception) {
            return this.write(write);
        }
    }

    public int pendingWrites() {
        synchronized (this.pending) {
            return this.pending.size();
        }
    }

    public void appendAsync(final File file, final String contents) {
        this.writer.execute(() -> {
            try {
                File parent = file.getParentFile();
                if (parent != null) Files.createDirectories(parent.toPath());
                Files.writeString(file.toPath(), contents, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException exception) {
                this.logger.warning("No se pudo añadir contenido a " + file.getAbsolutePath() + ": " + exception.getMessage());
            }
        });
    }

    public boolean flush() {
        try {
            this.writer.submit(() -> { }).get(15, TimeUnit.SECONDS);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | java.util.concurrent.TimeoutException exception) {
            this.logger.warning("No se pudo completar la cola de guardado: " + exception.getMessage());
            return false;
        }
    }

    private void scheduleDrain() {
        if (this.draining.compareAndSet(false, true)) this.writer.execute(this::drain);
    }

    private void drain() {
        try {
            // Un único escritor conserva el orden y evita escrituras simultáneas sobre un YAML.
            while (true) {
                PendingWrite write;
                synchronized (this.pending) {
                    if (this.pending.isEmpty()) return;
                    Path first = this.pending.keySet().iterator().next();
                    write = this.pending.remove(first);
                }
                this.write(write);
            }
        } finally {
            this.draining.set(false);
            synchronized (this.pending) {
                if (!this.pending.isEmpty()) this.scheduleDrain();
            }
        }
    }

    private boolean write(final PendingWrite pendingWrite) {
        try {
            AtomicYamlStorage.write(pendingWrite.file(), pendingWrite.contents());
            return true;
        } catch (IOException exception) {
            this.logger.severe("No se pudo guardar " + pendingWrite.file().getAbsolutePath() + ": " + exception.getMessage());
            return false;
        }
    }

    @Override
    public void close() {
        this.writer.shutdown();
        try {
            if (!this.writer.awaitTermination(15, TimeUnit.SECONDS)) {
                this.logger.warning("El guardado tardó demasiado; se completará lo pendiente de forma inmediata.");
                this.writer.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            this.writer.shutdownNow();
        }

        Map<Path, PendingWrite> remaining;
        synchronized (this.pending) {
            remaining = new LinkedHashMap<>(this.pending);
            this.pending.clear();
        }
        for (PendingWrite write : remaining.values()) this.write(write);
    }

    private record PendingWrite(File file, String contents) {
    }
}