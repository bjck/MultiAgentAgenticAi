package com.bko.files;

import java.util.List;

public record FileListing(
        String path,
        List<FileEntry> entries
) {
}
