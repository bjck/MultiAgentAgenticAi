package com.bko.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class HttpFetchTool {

    private static final Set<String> ALLOWED_METHODS = Set.of("GET", "POST", "PUT", "PATCH", "HEAD");
    private static final int DEFAULT_MAX_BYTES = 200_000;
    private static final int MAX_MAX_BYTES = 1_000_000;
    private static final int DEFAULT_TIMEOUT_SECONDS = 20;

    private final HttpClient httpClient;

    public HttpFetchTool() {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Tool(name = "http_fetch", description = "Fetch HTTP(S) content with size and timeout limits.")
    public HttpFetchResult fetch(@ToolParam(description = "HTTP fetch request") HttpFetchRequest request) throws Exception {
        if (request == null || !StringUtils.hasText(request.url())) {
            throw new IllegalArgumentException("url is required.");
        }
        URI uri = URI.create(request.url().trim());
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("Only http/https URLs are allowed.");
        }
        String method = StringUtils.hasText(request.method()) ? request.method().trim().toUpperCase(Locale.ROOT) : "GET";
        if (!ALLOWED_METHODS.contains(method)) {
            throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        }
        int timeoutSeconds = request.timeoutSeconds() != null && request.timeoutSeconds() > 0
                ? request.timeoutSeconds()
                : DEFAULT_TIMEOUT_SECONDS;
        int maxBytes = request.maxBytes() != null && request.maxBytes() > 0
                ? Math.min(request.maxBytes(), MAX_MAX_BYTES)
                : DEFAULT_MAX_BYTES;

        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(timeoutSeconds));
        if (request.headers() != null) {
            request.headers().forEach(builder::header);
        }
        if ("HEAD".equals(method)) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else if (StringUtils.hasText(request.body())) {
            builder.method(method, HttpRequest.BodyPublishers.ofString(request.body(), StandardCharsets.UTF_8));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }

        HttpResponse<InputStream> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        ReadResult readResult = readLimited(response.body(), maxBytes);
        String body = readResult.body();
        return new HttpFetchResult(
                uri.toString(),
                response.statusCode(),
                response.headers().map(),
                body,
                readResult.truncated(),
                readResult.bytesRead(),
                response.headers().firstValue("content-type").orElse(null)
        );
    }

    private ReadResult readLimited(InputStream input, int maxBytes) throws Exception {
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int remaining = maxBytes;
            boolean truncated = false;
            int bytesRead = 0;
            while (remaining > 0) {
                int read = in.read(buffer, 0, Math.min(buffer.length, remaining));
                if (read == -1) {
                    break;
                }
                out.write(buffer, 0, read);
                remaining -= read;
                bytesRead += read;
            }
            if (remaining == 0) {
                truncated = in.read() != -1;
            }
            String body = out.toString(StandardCharsets.UTF_8);
            return new ReadResult(body, truncated, bytesRead);
        }
    }

    public record HttpFetchRequest(
            String url,
            String method,
            Map<String, String> headers,
            String body,
            Integer timeoutSeconds,
            Integer maxBytes
    ) {}

    public record HttpFetchResult(
            String url,
            int status,
            Map<String, List<String>> headers,
            String body,
            boolean truncated,
            int bytesRead,
            String contentType
    ) {}

    private record ReadResult(String body, boolean truncated, int bytesRead) {}
}
