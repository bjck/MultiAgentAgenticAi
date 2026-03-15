package com.bko.api;

import com.bko.entity.ExternalDocument;
import com.bko.repository.ExternalDocumentRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST API for external documents (e.g. arXiv abstracts) stored for agent context.
 */
@RestController
@RequestMapping("/api/documents")
public class ExternalDocumentController {

    private static final String SOURCE_ARXIV = "arxiv";
    private static final int MAX_PAGE_SIZE = 500;

    private final ExternalDocumentRepository documentRepository;

    public ExternalDocumentController(ExternalDocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public DocumentListResponse list(
            @RequestParam(defaultValue = SOURCE_ARXIV) String source,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
        var pageable = PageRequest.of(Math.max(0, page), safeSize);
        String queryText = (q != null && !q.isBlank()) ? q.trim() : null;
        List<ExternalDocument> content = documentRepository.searchDocuments(source, queryText, pageable);
        long total = documentRepository.countSearchDocuments(source, queryText);
        return new DocumentListResponse(content, total, page, safeSize);
    }

    public record DocumentListResponse(
            List<ExternalDocument> content,
            long total,
            int page,
            int size
    ) {}
}
