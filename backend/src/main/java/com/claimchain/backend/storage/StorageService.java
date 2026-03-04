package com.claimchain.backend.storage;

import java.io.InputStream;

public interface StorageService {
    String save(InputStream data, String storageKey);
    InputStream load(String storageKey);
    long size(String storageKey);
    boolean exists(String storageKey);
}
