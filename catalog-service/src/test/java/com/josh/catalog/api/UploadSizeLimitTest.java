package com.josh.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * phase2_design_specification.md Immediate Fixes: uploads larger than the
 * configured multipart cap must be rejected cleanly, not silently accepted or
 * surfaced as a raw 500. Uses a real embedded server (not MockMvc) because
 * multipart size enforcement happens in the servlet container, and a raw
 * java.net.http.HttpClient request (not TestRestTemplate, which Spring Boot 4
 * no longer pulls in by default) to drive it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UploadSizeLimitTest {

    @TempDir
    static Path storageRoot;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("catalog.storage.root", () -> storageRoot.toString());
        registry.add("spring.servlet.multipart.max-file-size", () -> "1KB");
        registry.add("spring.servlet.multipart.max-request-size", () -> "1KB");
    }

    @LocalServerPort
    private int port;

    @Test
    void rejectsUploadsLargerThanTheConfiguredLimit() throws Exception {
        byte[] oversizedArchive = new byte[2048];
        Arrays.fill(oversizedArchive, (byte) 'x');

        String boundary = "----UploadSizeLimitTestBoundary";
        byte[] body = buildMultipartBody(boundary, oversizedArchive);

        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v1/skills"))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
            .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("exceeds the maximum allowed size");
    }

    private byte[] buildMultipartBody(String boundary, byte[] archiveBytes) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String crlf = "\r\n";

        out.write(("--" + boundary + crlf).getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"author\"" + crlf + crlf).getBytes(StandardCharsets.UTF_8));
        out.write(("tester" + crlf).getBytes(StandardCharsets.UTF_8));

        out.write(("--" + boundary + crlf).getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"archive\"; filename=\"oversized.zip\"" + crlf)
            .getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: application/zip" + crlf + crlf).getBytes(StandardCharsets.UTF_8));
        out.write(archiveBytes);
        out.write(crlf.getBytes(StandardCharsets.UTF_8));

        out.write(("--" + boundary + "--" + crlf).getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }
}
