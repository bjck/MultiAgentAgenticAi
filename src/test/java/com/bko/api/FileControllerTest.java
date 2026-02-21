package com.bko.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest(FileController.class)
class FileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private com.bko.files.FileService fileService;

    @Test
    void testListFiles() throws Exception {
        when(fileService.list(any())).thenReturn(new com.bko.files.FileListing("test", List.of()));

        mockMvc.perform(get("/api/files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries").isArray());
    }

    @Test
    void testWriteAndReadFile() throws Exception {
        String content = "{\"path\": \"test-api-file.txt\", \"content\": \"Hello API\"}";

        when(fileService.write(anyString(), anyString())).thenReturn(new com.bko.files.FileContent("test-api-file.txt", "Hello API"));
        when(fileService.read(anyString())).thenReturn(new com.bko.files.FileContent("test-api-file.txt", "Hello API"));

        mockMvc.perform(post("/api/files/content")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("test-api-file.txt"));

        mockMvc.perform(get("/api/files/content").param("path", "test-api-file.txt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Hello API"));
    }
}
