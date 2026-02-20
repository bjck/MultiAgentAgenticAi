package com.bko.files;

import com.bko.config.MultiAgentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileServiceTest {

    private FileService fileService;
    private MultiAgentProperties properties;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        properties = mock(MultiAgentProperties.class);
        when(properties.getWorkspaceRoot()).thenReturn(tempDir.toString());
        fileService = new FileService(properties);
    }

    @Test
    void testList() throws IOException {
        Files.createFile(tempDir.resolve("test.txt"));
        Files.createDirectory(tempDir.resolve("testdir"));

        FileListing listing = fileService.list("");
        assertEquals(2, listing.entries().size());
        assertTrue(listing.entries().stream().anyMatch(e -> e.name().equals("test.txt")));
        assertTrue(listing.entries().stream().anyMatch(e -> e.directory()));
    }

    @Test
    void testRead() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "Hello World");

        FileContent content = fileService.read("test.txt");
        assertEquals("Hello World", content.content());
        assertEquals("test.txt", content.path());
    }

    @Test
    void testReadNotFound() {
        assertThrows(ResponseStatusException.class, () -> fileService.read("nonexistent.txt"));
    }

    @Test
    void testWrite() throws IOException {
        FileContent content = fileService.write("newfile.txt", "New Content");
        assertEquals("New Content", content.content());
        
        Path filePath = tempDir.resolve("newfile.txt");
        assertTrue(Files.exists(filePath));
        assertEquals("New Content", Files.readString(filePath));
    }

    @Test
    void testWriteInSubdirectory() throws IOException {
        FileContent content = fileService.write("subdir/file.txt", "Sub Content");
        assertEquals("Sub Content", content.content());

        Path filePath = tempDir.resolve("subdir/file.txt");
        assertTrue(Files.exists(filePath));
        assertEquals("Sub Content", Files.readString(filePath));
    }

    @Test
    void testInvalidPath() {
        assertThrows(ResponseStatusException.class, () -> fileService.read("../outside.txt"));
    }
}
