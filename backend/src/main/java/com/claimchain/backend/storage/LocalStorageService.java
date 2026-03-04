package com.claimchain.backend.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
public class LocalStorageService implements StorageService {

    private final Path baseDir;

    public LocalStorageService(@Value("${storage.local.base-dir:storage/dev}") String baseDir) {
        this.baseDir = Path.of(baseDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.baseDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create storage base directory: " + this.baseDir, e);
        }
    }

    @Override
    public String save(InputStream data, String storageKey) {
        Path target = resolvePath(storageKey);

        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(data, target, StandardCopyOption.REPLACE_EXISTING);
            return storageKey;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save file for key: " + storageKey, e);
        }
    }

    @Override
    public InputStream load(String storageKey) {
        Path target = resolvePath(storageKey);
        try {
            return Files.newInputStream(target);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load file for key: " + storageKey, e);
        }
    }

    @Override
    public long size(String storageKey) {
        Path target = resolvePath(storageKey);
        try {
            return Files.size(target);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read size for key: " + storageKey, e);
        }
    }

    @Override
    public boolean exists(String storageKey) {
        Path target = resolvePath(storageKey);
        return Files.exists(target);
    }

    private Path resolvePath(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("Storage key must not be blank.");
        }

        String normalizedKey = storageKey.trim().replace('\\', '/');
        if (normalizedKey.startsWith("/") || normalizedKey.contains("..")) {
            throw new IllegalArgumentException("Invalid storage key.");
        }

        Path resolved = baseDir.resolve(normalizedKey).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new IllegalArgumentException("Invalid storage key.");
        }

        return resolved;
    }
}
