package com.bko.tools;

import com.bko.entity.ExternalDocument;
import com.bko.orchestration.service.ExternalDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
@Slf4j
public class ArxivApiReaderTool {

    private static final String ATOM_NS = "http://www.w3.org/2005/Atom";
    private static final String DEFAULT_SEARCH = "cat:cs.SE";
    private static final String DEFAULT_SORT_BY = "submittedDate";
    private static final String DEFAULT_SORT_ORDER = "descending";
    private static final int DEFAULT_MAX_RESULTS = 25;
    private static final int MAX_MAX_RESULTS = 100;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    /** Minimum delay between requests to comply with arXiv API terms of use (rate limits). */
    private static final long RATE_LIMIT_DELAY_MS = 3_000;
    private static final Object RATE_LOCK = new Object();
    private static OffsetDateTime lastRequestAt = OffsetDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC);

    private final ExternalDocumentService documentService;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Tool(name = "arxiv_api_reader",
            description = "Query the arXiv API, parse entries, and store abstracts as documentation. No PDFs are downloaded.")
    public ArxivIngestResult read(@ToolParam(description = "ArXiv API query options") ArxivQuery query) throws Exception {
        ArxivQuery normalized = normalize(query);
        try {
            enforceRateLimit();
            String url = buildUrl(normalized);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", "MultiAgent/1.0 (contact: admin@localhost)")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("arXiv API request failed: HTTP " + response.statusCode());
            }
            List<ArxivEntry> entries = parseEntries(response.body());
            List<String> sourceIds = entries.stream()
                    .map(ArxivEntry::arxivId)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();
            var existingIds = documentService.findExistingSourceIds("arxiv", sourceIds);
            List<ArxivItem> storedItems = new ArrayList<>();
            int skipped = 0;
            for (ArxivEntry entry : entries) {
                if (StringUtils.hasText(entry.arxivId()) && existingIds.contains(entry.arxivId())) {
                    skipped++;
                    continue;
                }
                ExternalDocument doc = ExternalDocument.builder()
                        .source("arxiv")
                        .sourceId(entry.arxivId())
                        .title(entry.title())
                        .abstractText(entry.summary())
                        .url(entry.url())
                        .authors(String.join(", ", entry.authors()))
                        .categories(String.join(", ", entry.categories()))
                        .sourcePublishedAt(entry.published())
                        .sourceUpdatedAt(entry.updated())
                        .build();
                documentService.upsert(doc);
                storedItems.add(new ArxivItem(entry.arxivId(), entry.title(), entry.url()));
            }
            return new ArxivIngestResult(normalized.searchQuery(), entries.size(), storedItems.size(), skipped, storedItems);
        } catch (Exception ex) {
            log.error("arxiv_api_reader failed for query '{}'", normalized.searchQuery(), ex);
            throw ex;
        }
    }

    private ArxivQuery normalize(ArxivQuery query) {
        String rawSearch = query != null ? query.searchQuery() : null;
        String search = normalizeSearchQuery(rawSearch);
        int start = query != null && query.start() != null && query.start() >= 0 ? query.start() : 0;
        int max = query != null && query.maxResults() != null && query.maxResults() > 0
                ? Math.min(query.maxResults(), MAX_MAX_RESULTS)
                : DEFAULT_MAX_RESULTS;
        String sortBy = StringUtils.hasText(query != null ? query.sortBy() : null)
                ? query.sortBy().trim()
                : DEFAULT_SORT_BY;
        String sortOrder = StringUtils.hasText(query != null ? query.sortOrder() : null)
                ? query.sortOrder().trim()
                : DEFAULT_SORT_ORDER;
        return new ArxivQuery(search, start, max, sortBy, sortOrder);
    }

    /**
     * Normalize human-friendly search input into a valid arXiv API search_query.
     * Examples:
     * - "software engineering" or "cs.se" -> "cat:cs.SE"
     * - null/blank -> DEFAULT_SEARCH ("cat:cs.SE")
     */
    private String normalizeSearchQuery(String raw) {
        if (!StringUtils.hasText(raw)) {
            return DEFAULT_SEARCH;
        }
        String trimmed = raw.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);

        // Common aliases for Software Engineering
        if ("software engineering".equals(lower)
                || "software-engineering".equals(lower)
                || "cs.se".equals(lower)
                || "csse".equals(lower)) {
            return DEFAULT_SEARCH;
        }

        // If user already provided a category query (cat:...), trust it
        if (lower.startsWith("cat:")) {
            return trimmed;
        }

        // Fallback: treat as a free-text query string
        return trimmed;
    }

    private String buildUrl(ArxivQuery query) {
        return "https://export.arxiv.org/api/query?search_query="
                + URLEncoder.encode(query.searchQuery(), StandardCharsets.UTF_8)
                + "&start=" + query.start()
                + "&max_results=" + query.maxResults()
                + "&sortBy=" + URLEncoder.encode(query.sortBy(), StandardCharsets.UTF_8)
                + "&sortOrder=" + URLEncoder.encode(query.sortOrder(), StandardCharsets.UTF_8);
    }

    private List<ArxivEntry> parseEntries(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        } catch (Exception ignore) {
            // ignore parser features if unsupported
        }
        var builder = factory.newDocumentBuilder();
        var doc = builder.parse(new InputSource(new StringReader(xml)));
        NodeList entryNodes = doc.getElementsByTagNameNS(ATOM_NS, "entry");
        List<ArxivEntry> entries = new ArrayList<>(entryNodes.getLength());
        for (int i = 0; i < entryNodes.getLength(); i++) {
            Element entry = (Element) entryNodes.item(i);
            String id = text(entry, ATOM_NS, "id");
            String title = normalizeWhitespace(text(entry, ATOM_NS, "title"));
            String summary = normalizeWhitespace(text(entry, ATOM_NS, "summary"));
            OffsetDateTime published = parseTime(text(entry, ATOM_NS, "published"));
            OffsetDateTime updated = parseTime(text(entry, ATOM_NS, "updated"));
            String url = extractLink(entry);
            String arxivId = extractArxivId(id);
            List<String> authors = extractAuthors(entry);
            List<String> categories = extractCategories(entry);
            entries.add(new ArxivEntry(arxivId, title, summary, url, authors, categories, published, updated));
        }
        return entries;
    }

    private String text(Element parent, String ns, String name) {
        NodeList nodes = parent.getElementsByTagNameNS(ns, name);
        if (nodes.getLength() == 0) {
            return "";
        }
        return nodes.item(0).getTextContent().trim();
    }

    private List<String> extractAuthors(Element entry) {
        NodeList authors = entry.getElementsByTagNameNS(ATOM_NS, "author");
        List<String> result = new ArrayList<>();
        for (int i = 0; i < authors.getLength(); i++) {
            Element author = (Element) authors.item(i);
            NodeList names = author.getElementsByTagNameNS(ATOM_NS, "name");
            if (names.getLength() > 0) {
                String name = names.item(0).getTextContent().trim();
                if (StringUtils.hasText(name)) {
                    result.add(name);
                }
            }
        }
        return result;
    }

    private List<String> extractCategories(Element entry) {
        NodeList categories = entry.getElementsByTagNameNS(ATOM_NS, "category");
        List<String> result = new ArrayList<>();
        for (int i = 0; i < categories.getLength(); i++) {
            Element category = (Element) categories.item(i);
            String term = category.getAttribute("term");
            if (StringUtils.hasText(term)) {
                result.add(term.trim());
            }
        }
        return result;
    }

    private String extractLink(Element entry) {
        NodeList links = entry.getElementsByTagNameNS(ATOM_NS, "link");
        String fallback = "";
        for (int i = 0; i < links.getLength(); i++) {
            Element link = (Element) links.item(i);
            String href = link.getAttribute("href");
            String rel = link.getAttribute("rel");
            String type = link.getAttribute("type");
            if (StringUtils.hasText(href)) {
                if ("alternate".equalsIgnoreCase(rel) && type.toLowerCase(Locale.ROOT).contains("html")) {
                    return href;
                }
                if (!StringUtils.hasText(fallback)) {
                    fallback = href;
                }
            }
        }
        return fallback;
    }

    private OffsetDateTime parseTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return OffsetDateTime.parse(value);
    }

    private String extractArxivId(String id) {
        if (!StringUtils.hasText(id)) {
            return "";
        }
        int idx = id.lastIndexOf("/abs/");
        String raw = idx >= 0 ? id.substring(idx + 5) : id;
        int queryIdx = raw.indexOf('?');
        return queryIdx >= 0 ? raw.substring(0, queryIdx) : raw;
    }

    private String normalizeWhitespace(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private void enforceRateLimit() throws InterruptedException {
        synchronized (RATE_LOCK) {
            OffsetDateTime now = OffsetDateTime.now();
            long elapsed = Duration.between(lastRequestAt, now).toMillis();
            if (elapsed < RATE_LIMIT_DELAY_MS) {
                Thread.sleep(RATE_LIMIT_DELAY_MS - elapsed);
            }
            lastRequestAt = OffsetDateTime.now();
        }
    }

    public record ArxivQuery(String searchQuery,
                             Integer start,
                             Integer maxResults,
                             String sortBy,
                             String sortOrder) {
    }

    public record ArxivIngestResult(String searchQuery,
                                    int fetched,
                                    int stored,
                                    int skipped,
                                    List<ArxivItem> items) {
    }

    public record ArxivItem(String sourceId, String title, String url) {
    }

    private record ArxivEntry(String arxivId,
                              String title,
                              String summary,
                              String url,
                              List<String> authors,
                              List<String> categories,
                              OffsetDateTime published,
                              OffsetDateTime updated) {
    }
}
