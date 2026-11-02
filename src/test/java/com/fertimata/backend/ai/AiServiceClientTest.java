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
    private final AtomicReference<String> contentLength = new AtomicReference<>();
    private final AtomicReference<String> transferEncoding = new AtomicReference<>();

    private final AtomicReference<String> method = new AtomicReference<>();
    private final AtomicReference<String> uri = new AtomicReference<>();

    private String serve(int status, String json) throws IOException {
        return serve(AiServiceClient.RUN_IRRIGATION, status, json);
    }

    private String serve(String path, int status, String json) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, exchange -> {
            method.set(exchange.getRequestMethod());
            uri.set(exchange.getRequestURI().toString());
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            contentLength.set(exchange.getRequestHeaders().getFirst("Content-Length"));
            transferEncoding.set(exchange.getRequestHeaders().getFirst("Transfer-Encoding"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
            byte[] out = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            if (status == 204) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
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
                new AiServiceProperties(baseUrl, Duration.ofSeconds(5), 2, Duration.ofHours(24), Duration.ofHours(72)),
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

    /** AI menolak tanpa foto, jadi panggilan yang diharapkan sampai ke AI butuh satu foto segar. */
    private void freshPhoto() throws IOException {
        photo("2026-10-31/fresh.jpg", NOW.minus(Duration.ofMinutes(30)));
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
        // Flask/Werkzeug membaca body chunked sebagai kosong: wajib Content-Length.
        assertThat(transferEncoding.get()).isNull();
        assertThat(Integer.parseInt(contentLength.get())).isEqualTo(requestBody.get().length());
        String body = requestBody.get();
        assertThat(body).contains("name=\"humidity_tanah\"").contains("35.5").contains("name=\"hst\"");
        assertThat(body).contains("filename=\"b.png\"").contains("filename=\"c.JPG\"");
        assertThat(body).doesNotContain("a.jpg").doesNotContain("old.jpg").doesNotContain("notes.txt");
    }

    @Test
    @DisplayName("an error answer from the AI service becomes an AiServiceException with its message")
    void errorAnswer() throws Exception {
        freshPhoto();
        String url = serve(400, "{\"status\":\"error\",\"message\":\"Input yang dimasukkan harus berupa string angka\"}");

        assertThatThrownBy(() -> client(url).irrigationSeconds(form()))
                .isInstanceOf(AiServiceException.class)
                .hasMessageContaining("400")
                .hasMessageContaining("string angka");
    }

    @Test
    @DisplayName("an unreachable AI service becomes an AiServiceException")
    void unreachable() throws Exception {
        freshPhoto();
        assertThatThrownBy(() -> client("http://127.0.0.1:1").irrigationSeconds(form()))
                .isInstanceOf(AiServiceException.class);
    }

    @Test
    @DisplayName("an empty field is refused before calling the AI")
    void emptyField() throws Exception {
        freshPhoto();
        Map<String, BigDecimal> form = form();
        form.put("ph", null);

        assertThatThrownBy(() -> client("http://127.0.0.1:1").irrigationSeconds(form))
                .isInstanceOf(AiServiceException.class)
                .hasMessageContaining("ph");
    }

    @Test
    @DisplayName("no patrol photo: NoPhotoException and the AI is not called (it refuses requests without a photo)")
    void noPhoto() throws Exception {
        photo("2026-10-28/old.jpg", NOW.minus(Duration.ofDays(4)));
        String url = serve(200, "{\"status\":\"success\",\"result\":{\"durasi_irigasi\":47.25}}");

        assertThatThrownBy(() -> client(url).irrigationSeconds(form())).isInstanceOf(NoPhotoException.class);
        assertThatThrownBy(() -> client(url).fertilizationDose(form())).isInstanceOf(NoPhotoException.class);
        assertThat(requestBody.get()).isNull();
    }

    @Test
    @DisplayName("fertilization accepts photos up to fertilization-photo-max-age (72 h); irrigation only 24 h")
    void fertilizationPhotoAge() throws Exception {
        photo("2026-10-29/two-days.jpg", NOW.minus(Duration.ofHours(48)));
        String url = serve(AiServiceClient.RUN_FERTILIZATION, 200,
                "{\"status\":\"success\",\"result\":{\"durasi_pompa_n\":1,\"durasi_pompa_p\":2,\"durasi_pompa_k\":3}}");

        assertThatThrownBy(() -> client(url).irrigationSeconds(form())).isInstanceOf(NoPhotoException.class);
        assertThat(client(url).fertilizationDose(form())).isEqualTo(new AiServiceClient.Dose(1, 2, 3));
        assertThat(requestBody.get()).contains("filename=\"two-days.jpg\"");
    }

    @Test
    @DisplayName("run_fertilization: posts the form and reads durasi_pompa_n/p/k")
    void fertilizationDose() throws Exception {
        freshPhoto();
        String url = serve(AiServiceClient.RUN_FERTILIZATION, 200,
                "{\"status\":\"success\",\"result\":{\"durasi_pompa_n\":12.5,\"durasi_pompa_p\":0,\"durasi_pompa_k\":150.0}}");
        Map<String, BigDecimal> form = new LinkedHashMap<>();
        form.put("ec", new BigDecimal("1.8"));
        form.put("hst", BigDecimal.valueOf(20));

        AiServiceClient.Dose dose = client(url).fertilizationDose(form);

        assertThat(dose).isEqualTo(new AiServiceClient.Dose(12.5, 0, 150));
        assertThat(requestBody.get()).contains("name=\"ec\"").contains("1.8");
    }

    @Test
    @DisplayName("GET /api/schedule/fertilization: rows with id and interval_days")
    void fertilizationIntervals() throws Exception {
        String url = serve(AiServiceClient.FERTILIZATION_SCHEDULE, 200,
                "{\"status\":\"success\",\"result\":[{\"id\":4,\"interval_days\":7}]}");

        assertThat(client(url).fertilizationIntervals())
                .containsExactly(new AiServiceClient.FertilizationInterval(4, 7));
        assertThat(method.get()).isEqualTo("GET");
    }

    @Test
    @DisplayName("PUT /api/schedule/fertilization sends JSON {id, interval} and reads data")
    void updateInterval() throws Exception {
        String url = serve(AiServiceClient.FERTILIZATION_SCHEDULE, 200,
                "{\"status\":\"success\",\"data\":{\"id\":4,\"interval_days\":5},\"message\":\"ok\"}");

        assertThat(client(url).updateFertilizationInterval(4, 5))
                .isEqualTo(new AiServiceClient.FertilizationInterval(4, 5));
        assertThat(method.get()).isEqualTo("PUT");
        assertThat(contentType.get()).startsWith("application/json");
        assertThat(requestBody.get()).contains("\"id\":4").contains("\"interval\":5");
    }

    @Test
    @DisplayName("DELETE /api/schedule/fertilization/{id} accepts the AI's 204")
    void deleteInterval() throws Exception {
        String url = serve(AiServiceClient.FERTILIZATION_SCHEDULE, 204, "");

        client(url).deleteFertilizationInterval(4);

        assertThat(method.get()).isEqualTo("DELETE");
        assertThat(uri.get()).isEqualTo("/api/schedule/fertilization/4");
    }

    @Test
    @DisplayName("history rows are passed through with the limit")
    void history() throws Exception {
        String url = serve("/api/history/irrigation", 200,
                "{\"status\":\"success\",\"result\":[{\"id\":\"a1\",\"watering_duration\":48.7}]}");

        assertThat(client(url).history(AiServiceClient.HistoryKind.IRRIGATION, 5))
                .containsExactly(Map.of("id", "a1", "watering_duration", 48.7));
        assertThat(uri.get()).isEqualTo("/api/history/irrigation?limit=5");
    }

    @Test
    @DisplayName("an AI answer with status error is refused even with HTTP 200")
    void statusError() throws Exception {
        String url = serve(AiServiceClient.FERTILIZATION_SCHEDULE, 200, "{\"status\":\"error\",\"message\":\"db down\"}");

        assertThatThrownBy(() -> client(url).fertilizationIntervals())
                .isInstanceOf(AiServiceException.class)
                .hasMessageContaining("db down");
    }

    @Test
    @DisplayName("GET /health returns the message")
    void health() throws Exception {
        String url = serve(AiServiceClient.HEALTH, 200, "{\"message\":\"Running Melon AI System API\"}");

        assertThat(client(url).health()).isEqualTo("Running Melon AI System API");
    }
}
