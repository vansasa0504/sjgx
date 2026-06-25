package com.platform.pipeline.storage.tier;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class LocalColdStorageStore implements ColdStorageStore {
    private final Path directory;

    public LocalColdStorageStore() {
        this(Path.of(System.getenv().getOrDefault("COLD_STORAGE_DIR", "./target/cold-storage")));
    }

    public LocalColdStorageStore(Path directory) {
        this.directory = directory;
    }

    @Override
    public void write(TieredRecord record) {
        try {
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(record.key() + ".data"), record.payload(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("cold storage write failed", ex);
        }
    }

    @Override
    public List<TieredRecord> readAll() {
        try {
            if (!Files.exists(directory)) {
                return List.of();
            }
            try (var stream = Files.list(directory)) {
                return stream.filter(path -> path.getFileName().toString().endsWith(".data"))
                        .map(path -> read(path))
                        .toList();
            }
        } catch (Exception ex) {
            throw new IllegalStateException("cold storage read failed", ex);
        }
    }

    private TieredRecord read(Path path) {
        try {
            String file = path.getFileName().toString();
            return new TieredRecord(file.substring(0, file.length() - 5), Files.readString(path), StorageTier.COLD);
        } catch (Exception ex) {
            throw new IllegalStateException("cold storage read failed", ex);
        }
    }
}
