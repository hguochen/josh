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
 * Thin HTTP client for the Catalog Service (design_specifications.md Section 8,
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

    HttpResult search(String query) {
        URI uri = baseUri.resolve("/v1/skills?q=" + urlEncode(query));
        return send(HttpRequest.newBuilder(uri).GET());
    }

    HttpResult retrieve(String name, Integer version) {
        String path = "/v1/skills/" + urlEncode(name);
        if (version != null) {
            path += "?version=" + version;
        }
        return send(HttpRequest.newBuilder(baseUri.resolve(path)).GET());
    }

    HttpResult history(String name) {
        URI uri = baseUri.resolve("/v1/skills/" + urlEncode(name) + "/versions");
        return send(HttpRequest.newBuilder(uri).GET());
    }

    HttpResult publish(byte[] archiveBytes, String author, String filename) {
        String boundary = "----JoshBoundary" + new SecureRandom().nextLong();
        byte[] body = buildMultipartBody(boundary, archiveBytes, author, filename);

        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("/v1/skills"))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
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

    private byte[] buildMultipartBody(String boundary, byte[] archiveBytes, String author, String filename) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            String crlf = "\r\n";

            out.write(("--" + boundary + crlf).getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Disposition: form-data; name=\"author\"" + crlf + crlf).getBytes(StandardCharsets.UTF_8));
            out.write((author + crlf).getBytes(StandardCharsets.UTF_8));

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
