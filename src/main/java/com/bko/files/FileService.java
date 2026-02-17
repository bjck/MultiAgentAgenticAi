package com.bko.files;

import com.bko.config.MultiAgentProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Service
public class FileService {

    private final Path workspaceRoot;

    public FileService(MultiAgentProperties properties) {
        String configuredRoot = properties.getWorkspaceRoot();
        String rootValue = StringUtils.hasText(configuredRoot)
                ? configuredRoot
                : System.getProperty("user.dir");
        this.workspaceRoot = Paths.get(rootValue).toAbsolutePath().normalize();
    }

    public FileListing list(String path) {
        Path directory = resolvePath(path, true);
        if (!Files.exists(directory)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Directory not found.");
        }
        if (!Files.isDirectory(directory)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Path is not a directory.");
        }
        try (Stream<Path> stream = Files.list(directory)) {
            List<FileEntry> entries = stream
                    .sorted(Comparator.comparing((Path p) -> !Files.isDirectory(p))
                            .thenComparing(p -> p.getFileName().toString().toLowerCase()))
                    .map(this::toEntry)
                    .toList();
            return new FileListing(toRelative(directory), entries);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to list directory.");
        }
    }

    public FileContent read(String path) {
        Path file = resolvePath(path, true);
        if (!Files.exists(file)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found.");
        }
        if (!Files.isRegularFile(file)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Path is not a file.");
        }
        try {
            String content = Files.readString(file);
            return new FileContent(toRelative(file), content);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read file.");
        }
    }

    public FileContent write(String path, String content) {
        Path file = resolvePath(path, false);
        if (Files.exists(file) && !Files.isRegularFile(file)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Path is not a file.");
        }
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, content == null ? "" : content);
            return new FileContent(toRelative(file), content == null ? "" : content);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to write file.");
        }
    }

    private Path resolvePath(String path, boolean allowDirectoryRoot) {
        Path target;
        if (!StringUtils.hasText(path)) {
            if (!allowDirectoryRoot) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Path is required.");
            }
            return workspaceRoot;
        }
        target = workspaceRoot.resolve(path).normalize();
        if (!target.startsWith(workspaceRoot)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid path.");
        }
        return target;
    }

    private FileEntry toEntry(Path path) {
        try {
            return new FileEntry(
                    path.getFileName().toString(),
                    toRelative(path),
                    Files.isDirectory(path),
                    Files.isDirectory(path) ? 0L : Files.size(path),
                    Instant.ofEpochMilli(Files.getLastModifiedTime(path).toMillis())
            );
        } catch (IOException ex) {
            return new FileEntry(
                    path.getFileName().toString(),
                    toRelative(path),
                    Files.isDirectory(path),
                    0L,
                    Instant.EPOCH
            );
        }
    }

    private String toRelative(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (normalized.equals(workspaceRoot)) {
            return "";
        }
        return workspaceRoot.relativize(normalized).toString().replace("\\", "/");
    }
}
