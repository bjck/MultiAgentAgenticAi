package com.bko.files;

import java.time.Instant;

public record FileEntry(
        String name,
        String path,
        boolean directory,
        long size,
        Instant lastModified
) {
}
