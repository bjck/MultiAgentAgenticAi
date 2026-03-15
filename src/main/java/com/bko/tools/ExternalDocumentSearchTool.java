package com.bko.tools;

import com.bko.entity.ExternalDocument;
import com.bko.repository.ExternalDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ExternalDocumentSearchTool {

    private static final String DEFAULT_SOURCE = "arxiv";
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;

    private final ExternalDocumentRepository externalDocumentRepository;

    @Tool(
            name = "external_document_search",
            description = "Search persisted external documents (including arXiv abstracts) from the database."
    )
    public ExternalDocumentSearchResult search(
            @ToolParam(description = "Search options for external_document records")
            ExternalDocumentSearchRequest request
    ) {
        String source = normalizeSource(request);
        String queryText = normalizeQueryText(request);
        int limit = normalizeLimit(request != null ? request.limit() : null);

        List<ExternalDocument> docs = externalDocumentRepository.searchDocuments(
                source,
                queryText,
                PageRequest.of(0, limit)
        );
        List<ExternalDocumentMatch> matches = docs.stream()
                .map(this::toMatch)
                .toList();
        return new ExternalDocumentSearchResult(
                source,
                queryText,
                null,
                matches.size(),
                matches
        );
    }

    private String normalizeSource(ExternalDocumentSearchRequest request) {
        String value = request != null ? request.source() : null;
        return StringUtils.hasText(value) ? value.trim() : DEFAULT_SOURCE;
    }

    private String normalizeQueryText(ExternalDocumentSearchRequest request) {
        String value = request != null ? request.queryText() : null;
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private ExternalDocumentMatch toMatch(ExternalDocument doc) {
        return new ExternalDocumentMatch(
                doc.getSource(),
                doc.getSourceId(),
                doc.getTitle(),
                truncate(doc.getAbstractText(), 1_200),
                doc.getUrl(),
                doc.getAuthors(),
                doc.getCategories(),
                doc.getSourcePublishedAt()
        );
    }

    private String truncate(String value, int maxChars) {
        if (!StringUtils.hasText(value) || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars) + "...";
    }

    public record ExternalDocumentSearchRequest(
            String source,
            String queryText,
            String agentId,
            Integer limit
    ) {
    }

    public record ExternalDocumentSearchResult(
            String source,
            String queryText,
            String agentId,
            int returned,
            List<ExternalDocumentMatch> matches
    ) {
    }

    public record ExternalDocumentMatch(
            String source,
            String sourceId,
            String title,
            String abstractText,
            String url,
            String authors,
            String categories,
            OffsetDateTime sourcePublishedAt
    ) {
    }
}
