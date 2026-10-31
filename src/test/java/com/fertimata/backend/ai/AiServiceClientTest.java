package com.fertimata.backend.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fertimata.backend.captures.CapturesProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Memakai server HTTP kecil yang meniru Flask tim AI (Ai-code/app/routes/fuzzy_fertigations.py). */
class AiServiceClientTest {

    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");

    @TempDir
    Path captures;

    private HttpServer server;
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> contentType = new AtomicReference<>();

    private String serve(int status, String json) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(AiServiceClient.RUN_IRRIGATION, exchange -> {
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
            byte[] out = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private AiServiceClient client(String baseUrl) {
        return new AiServiceClient(
                new AiServiceProperties(baseUrl, Duration.ofSeconds(5), 2, Duration.ofHours(24)),
                new CapturesProperties(captures.toString(), null),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Map<String, BigDecimal> form() {
        Map<String, BigDecimal> form = new LinkedHashMap<>();
        form.put("humidity_tanah", new BigDecimal("35.5"));
        form.put("hst", BigDecimal.valueOf(12));
        return form;
    }

    private Path photo(String name, Instant modified) throws IOException {
        Path file = captures.resolve(name);
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[] {1, 2, 3});
        Files.setLastModifiedTime(file, FileTime.from(modified));
        return file;
    }

    @Test
    @DisplayName("posts the form fields and the newest patrol photos, and reads result.durasi_irigasi")
    void postsFormAndPhotos() throws Exception {
        photo("2026-10-30/old.jpg", NOW.minus(Duration.ofDays(3)));
        photo("2026-10-30/a.jpg", NOW.minus(Duration.ofHours(3)));
        photo("2026-10-31/b.png", NOW.minus(Duration.ofHours(1)));
        photo("2026-10-31/c.JPG", NOW.minus(Duration.ofHours(2)));
        photo("2026-10-31/notes.txt", NOW);
        String url = serve(200, "{\"status\":\"success\",\"result\":{\"durasi_irigasi\":47.25}}");

        double seconds = client(url).irrigationSeconds(form());

        assertThat(seconds).isEqualTo(47.25);
        assertThat(contentType.get()).startsWith("multipart/form-data");
        String body = requestBody.get();
        assertThat(body).contains("name=\"humidity_tanah\"").contains("35.5").contains("name=\"hst\"");
        assertThat(body).contains("filename=\"b.png\"").contains("filename=\"c.JPG\"");
        assertThat(body).doesNotContain("a.jpg").doesNotContain("old.jpg").doesNotContain("notes.txt");
    }

    @Test
    @DisplayName("an error answer from the AI service becomes an AiServiceException with its message")
    void errorAnswer() throws Exception {
        String url = serve(400, "{\"status\":\"error\",\"message\":\"Input yang dimasukkan harus berupa string angka\"}");

        assertThatThrownBy(() -> client(url).irrigationSeconds(form()))
                .isInstanceOf(AiServiceException.class)
                .hasMessageContaining("400")
                .hasMessageContaining("string angka");
    }

    @Test
    @DisplayName("an unreachable AI service becomes an AiServiceException")
    void unreachable() {
        assertThatThrownBy(() -> client("http://127.0.0.1:1").irrigationSeconds(form()))
                .isInstanceOf(AiServiceException.class);
    }

    @Test
    @DisplayName("an empty field is refused before calling the AI")
    void emptyField() {
        Map<String, BigDecimal> form = form();
        form.put("ph", null);

        assertThatThrownBy(() -> client("http://127.0.0.1:1").irrigationSeconds(form))
                .isInstanceOf(AiServiceException.class)
                .hasMessageContaining("ph");
    }
}
