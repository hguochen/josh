package com.josh.mcpadapter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;

/**
 * Thin HTTP client for the Catalog Service (phase1_design_specifications.md Section 8,
 * API Design). Translates MCP tool calls into HTTP calls — no business logic
 * lives here, that stays server-side.
 */
final class CatalogClient {

    private final HttpClient httpClient;
    private final URI baseUri;

    CatalogClient(String baseUrl) {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.baseUri = URI.create(baseUrl);
    }

    record HttpResult(int status, byte[] body, HttpHeaders headers) {
        String bodyAsUtf8() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    /**
     * {@code author}, when given, also surfaces that caller's own personal
     * skills alongside the shared catalog (phase2_design_specification.md,
     * Features) — never another developer's.
     */
    HttpResult search(String query, String author) {
        String path = "/v1/skills?q=" + urlEncode(query);
        if (author != null && !author.isBlank()) {
            path += "&author=" + urlEncode(author);
        }
        return send(HttpRequest.newBuilder(baseUri.resolve(path)).GET());
    }

    HttpResult retrieve(String name, Integer version, String author) {
        String path = "/v1/skills/" + urlEncode(name);
        String query = "";
        if (version != null) {
            query += "version=" + version;
        }
        if (author != null && !author.isBlank()) {
            query += (query.isEmpty() ? "" : "&") + "author=" + urlEncode(author);
        }
        if (!query.isEmpty()) {
            path += "?" + query;
        }
        return send(HttpRequest.newBuilder(baseUri.resolve(path)).GET());
    }

    HttpResult history(String name, String author) {
        String path = "/v1/skills/" + urlEncode(name) + "/versions";
        if (author != null && !author.isBlank()) {
            path += "?author=" + urlEncode(author);
        }
        return send(HttpRequest.newBuilder(baseUri.resolve(path)).GET());
    }

    /** {@code visibility}: null/"shared" publishes to the shared catalog (default), "private" to the caller's own. */
    HttpResult publish(byte[] archiveBytes, String author, String filename, String visibility) {
        String boundary = "----JoshBoundary" + new SecureRandom().nextLong();
        byte[] body = buildMultipartBody(boundary, archiveBytes, author, filename, visibility);

        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("/v1/skills"))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build();
        return send(request);
    }

    /** phase2_design_specification.md Features: promote the caller's own latest personal version of {@code name} into shared. */
    HttpResult promote(String name, String author) {
        String path = "/v1/skills/" + urlEncode(name) + "/promote?author=" + urlEncode(author);
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(path))
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
        return send(request);
    }

    private HttpResult send(HttpRequest.Builder requestBuilder) {
        return send(requestBuilder.build());
    }

    private HttpResult send(HttpRequest request) {
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            return new HttpResult(response.statusCode(), response.body(), response.headers());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to reach Catalog Service at " + baseUri, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while calling Catalog Service", e);
        }
    }

    private byte[] buildMultipartBody(String boundary, byte[] archiveBytes, String author, String filename, String visibility) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            String crlf = "\r\n";

            out.write(("--" + boundary + crlf).getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Disposition: form-data; name=\"author\"" + crlf + crlf).getBytes(StandardCharsets.UTF_8));
            out.write((author + crlf).getBytes(StandardCharsets.UTF_8));

            if (visibility != null && !visibility.isBlank()) {
                out.write(("--" + boundary + crlf).getBytes(StandardCharsets.UTF_8));
                out.write(("Content-Disposition: form-data; name=\"visibility\"" + crlf + crlf).getBytes(StandardCharsets.UTF_8));
                out.write((visibility + crlf).getBytes(StandardCharsets.UTF_8));
            }

            out.write(("--" + boundary + crlf).getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Disposition: form-data; name=\"archive\"; filename=\"" + filename + "\"" + crlf)
                .getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Type: application/zip" + crlf + crlf).getBytes(StandardCharsets.UTF_8));
            out.write(archiveBytes);
            out.write(crlf.getBytes(StandardCharsets.UTF_8));

            out.write(("--" + boundary + "--" + crlf).getBytes(StandardCharsets.UTF_8));
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
