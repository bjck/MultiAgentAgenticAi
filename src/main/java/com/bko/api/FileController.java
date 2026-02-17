package com.bko.api;

import com.bko.files.FileContent;
import com.bko.files.FileListing;
import com.bko.files.FileService;
import com.bko.files.WriteFileRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @GetMapping
    public FileListing list(@RequestParam(value = "path", required = false) String path) {
        return fileService.list(path);
    }

    @GetMapping("/content")
    public FileContent read(@RequestParam("path") String path) {
        return fileService.read(path);
    }

    @PostMapping("/content")
    public FileContent write(@Valid @RequestBody WriteFileRequest request) {
        return fileService.write(request.path(), request.content());
    }
}
