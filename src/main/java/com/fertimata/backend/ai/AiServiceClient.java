package com.fertimata.backend.ai;

import com.fertimata.backend.captures.CapturesProperties;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Memanggil layanan AI tim AI (repo Fertigation_Service). Kode AI tidak diubah: backend mengikuti route dan Swagger
 * mereka ({@code ai-code/app/routes}, {@code ai-code/app/docs/swagger_docs.py}). Semua jawaban AI berbentuk
 * {@code {"status": "success"|"error", "result"|"data"|"message": ...}}.
 */
@Component
public class AiServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AiServiceClient.class);
    static final String RUN_IRRIGATION = "/api/fuzzy_sys/run_irrigation";
    static final String RUN_FERTILIZATION = "/api/fuzzy_sys/run_fertilization";
    static final String FERTILIZATION_SCHEDULE = "/api/schedule/fertilization";
    static final String HISTORY = "/api/history/";
    static final String DETECT = "/api/detect/";
    static final String HEALTH = "/health";
    private static final List<String> PHOTO_EXTENSIONS = List.of(".jpg", ".jpeg", ".png");
    private static final int PHOTO_SEARCH_DEPTH = 6;

    /** Durasi pompa dosing (detik) dari fuzzy fertigasi AI. */
    public record Dose(double nitrogenSeconds, double phosphorusSeconds, double potassiumSeconds) {}

    /** Satu baris {@code dosing_schedules} layanan AI: dosing tiap {@code intervalDays} hari. */
    public record FertilizationInterval(long id, int intervalDays) {}

    /** Jenis riwayat inferensi AI ({@code /api/history/{kind}}). */
    public enum HistoryKind {
        IRRIGATION("irrigation"),
        FERTILIZATION("fertilization");

        private final String path;

        HistoryKind(String path) {
            this.path = path;
        }
    }

    /** Model YOLO layanan AI ({@code /api/detect/{kind}}). */
    public enum DetectionKind {
        DEFICIENCY("deficiency"),
        DISEASE("disease");

        private final String path;

        DetectionKind(String path) {
            this.path = path;
        }
    }

    private final AiServiceProperties properties;
    private final CapturesProperties captures;
    private final Clock clock;
    private final RestClient restClient;

    public AiServiceClient(AiServiceProperties properties, CapturesProperties captures, Clock clock) {
        this.properties = properties;
        this.captures = captures;
        this.clock = clock;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.timeout()).build());
        factory.setReadTimeout(properties.timeout());
        // Buffered: body dikirim dengan Content-Length, bukan chunked. Server WSGI (Flask/Werkzeug) bisa
        // membaca body chunked sebagai kosong, sehingga form sampai ke AI tanpa isi.
        this.restClient = RestClient.builder()
                .requestFactory(new BufferingClientHttpRequestFactory(factory))
                .build();
    }

    public boolean configured() {
        return properties.configured();
    }

    /**
     * Durasi siram (detik) dari fuzzy irigasi AI.
     *
     * <p>Foto patroli terbaru ikut dikirim: {@code run_irrigation} menjalankan deteksi penyakit dan
     * membatasi siram bila ada penyakit.
     *
     * @param form field form {@code run_irrigation} dari {@link AiInputsResponse#irrigation()}; semua wajib terisi
     * @throws NoPhotoException tidak ada foto patroli dalam {@code photo-max-age}; AI tidak dipanggil
     */
    public double irrigationSeconds(Map<String, BigDecimal> form) throws AiServiceException {
        List<Path> photos = requirePhotos(properties.photoMaxAge());
        Map<?, ?> result = result(postForm(RUN_IRRIGATION, "Irrigation", form, photos), RUN_IRRIGATION);
        double seconds = number(result, "durasi_irigasi", RUN_IRRIGATION);
        log.info("AI fuzzy irrigation: {} s ({} photo(s) sent)", seconds, photos.size());
        return seconds;
    }

    /**
     * Durasi pompa dosing N, P, K (detik) dari fuzzy fertigasi AI.
     *
     * <p>Foto patroli terbaru ikut dikirim: {@code run_fertilization} memperkirakan defisiensi N/P/K dari foto daun.
     * Layanan AI mencatat setiap panggilan ini di riwayatnya sendiri.
     *
     * @param form field form {@code run_fertilization} dari {@link AiInputsResponse#fertilization()}; semua wajib terisi
     * @throws NoPhotoException tidak ada foto patroli dalam {@code fertilization-photo-max-age}; AI tidak dipanggil
     */
    public Dose fertilizationDose(Map<String, BigDecimal> form) throws AiServiceException {
        List<Path> photos = requirePhotos(properties.fertilizationPhotoMaxAge());
        Map<?, ?> result = result(postForm(RUN_FERTILIZATION, "Fertilization", form, photos), RUN_FERTILIZATION);
        Dose dose = new Dose(
                number(result, "durasi_pompa_n", RUN_FERTILIZATION),
                number(result, "durasi_pompa_p", RUN_FERTILIZATION),
                number(result, "durasi_pompa_k", RUN_FERTILIZATION));
        log.info("AI fuzzy fertilization: N {} s, P {} s, K {} s ({} photo(s) sent)",
                dose.nitrogenSeconds(), dose.phosphorusSeconds(), dose.potassiumSeconds(), photos.size());
        return dose;
    }

    /** Jadwal dosing di layanan AI ({@code GET /api/schedule/fertilization}); kosong = belum diatur. */
    public List<FertilizationInterval> fertilizationIntervals() throws AiServiceException {
        Object result = send(restClient.get().uri(url(FERTILIZATION_SCHEDULE)), FERTILIZATION_SCHEDULE).get("result");
        if (!(result instanceof List<?> rows)) {
            throw new AiServiceException("AI service answer has no result list: " + result);
        }
        List<FertilizationInterval> intervals = new ArrayList<>();
        for (Object row : rows) {
            intervals.add(interval(row));
        }
        return intervals;
    }

    /** {@code POST /api/schedule/fertilization}: membuat jadwal dosing tiap {@code intervalDays} hari. */
    public FertilizationInterval createFertilizationInterval(int intervalDays) throws AiServiceException {
        Map<?, ?> response = send(
                restClient.post().uri(url(FERTILIZATION_SCHEDULE)).contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("interval", intervalDays)),
                FERTILIZATION_SCHEDULE);
        return interval(response.get("data"));
    }

    /** {@code PUT /api/schedule/fertilization}: mengganti interval jadwal dosing {@code id}. */
    public FertilizationInterval updateFertilizationInterval(long id, int intervalDays) throws AiServiceException {
        Map<?, ?> response = send(
                restClient.put().uri(url(FERTILIZATION_SCHEDULE)).contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("id", id, "interval", intervalDays)),
                FERTILIZATION_SCHEDULE);
        return interval(response.get("data"));
    }

    /** {@code DELETE /api/schedule/fertilization/{id}}. */
    public void deleteFertilizationInterval(long id) throws AiServiceException {
        sendNoBody(restClient.delete().uri(url(FERTILIZATION_SCHEDULE + "/" + id)), FERTILIZATION_SCHEDULE);
    }

    /** Riwayat inferensi yang dicatat layanan AI sendiri ({@code GET /api/history/{kind}?limit=}), terbaru dulu. */
    public List<Map<String, Object>> history(HistoryKind kind, int limit) throws AiServiceException {
        String path = HISTORY + kind.path;
        Object result = send(restClient.get().uri(url(path) + "?limit=" + limit), path).get("result");
        if (!(result instanceof List<?> rows)) {
            throw new AiServiceException("AI service answer has no result list: " + result);
        }
        List<Map<String, Object>> history = new ArrayList<>();
        for (Object row : rows) {
            if (row instanceof Map<?, ?> map) {
                Map<String, Object> copy = new LinkedHashMap<>();
                map.forEach((k, v) -> copy.put(String.valueOf(k), v));
                history.add(copy);
            }
        }
        return history;
    }

    /**
     * Deteksi YOLO ({@code POST /api/detect/{kind}}). Hasilnya diteruskan apa adanya: defisiensi = persen per kelas,
     * penyakit = daftar label.
     */
    public Object detect(DetectionKind kind, List<? extends Resource> images) throws AiServiceException {
        if (images.isEmpty()) {
            throw new AiServiceException("No photo to send to the AI service");
        }
        String path = DETECT + kind.path;
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        images.forEach(image -> body.add("images", image));
        Map<?, ?> response = send(
                restClient.post().uri(url(path)).contentType(MediaType.MULTIPART_FORM_DATA).body(body), path);
        return response.get("result");
    }

    /** {@code GET /health}: pesan layanan AI bila hidup. */
    public String health() throws AiServiceException {
        Map<?, ?> response;
        requireConfigured();
        try {
            response = restClient.get().uri(url(HEALTH)).retrieve().body(Map.class);
        } catch (RestClientResponseException ex) {
            throw answered(ex);
        } catch (RestClientException ex) {
            throw new AiServiceException("AI service unreachable: " + ex.getMessage(), ex);
        }
        return response == null ? null : String.valueOf(response.get("message"));
    }

    /** AI menolak permintaan tanpa foto, jadi tanpa foto segar AI tidak dipanggil. */
    private List<Path> requirePhotos(Duration maxAge) throws NoPhotoException {
        List<Path> photos = recentPhotos(maxAge);
        if (photos.isEmpty()) {
            throw new NoPhotoException("No patrol photo from the last %s; the AI service needs at least one"
                    .formatted(maxAge));
        }
        return photos;
    }

    /** Foto patroli dalam {@code photo-max-age} (paling banyak {@code irrigation-photos}), terbaru dulu. */
    public List<Path> recentPhotos() {
        return recentPhotos(properties.photoMaxAge());
    }

    private List<Path> recentPhotos(Duration maxAge) {
        if (!captures.enabled() || properties.irrigationPhotos() <= 0) {
            return List.of();
        }
        Path root = Path.of(captures.directory());
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        Instant oldest = clock.instant().minus(maxAge);
        try (Stream<Path> files = Files.walk(root, PHOTO_SEARCH_DEPTH)) {
            return files.filter(Files::isRegularFile)
                    .filter(AiServiceClient::isPhoto)
                    .map(p -> Map.entry(p, modified(p)))
                    .filter(e -> e.getValue().toInstant().isAfter(oldest))
                    .sorted(Map.Entry.<Path, FileTime>comparingByValue(Comparator.reverseOrder()))
                    .limit(properties.irrigationPhotos())
                    .map(Map.Entry::getKey)
                    .toList();
        } catch (IOException | RuntimeException ex) {
            log.warn("Could not list patrol photos in {}: {}", root, ex.toString());
            return List.of();
        }
    }

    private Map<?, ?> postForm(String path, String formName, Map<String, BigDecimal> form, List<Path> photos)
            throws AiServiceException {
        requireConfigured();
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        for (Map.Entry<String, BigDecimal> field : form.entrySet()) {
            if (field.getValue() == null) {
                throw new AiServiceException(formName + " field " + field.getKey() + " is empty");
            }
            body.add(field.getKey(), field.getValue().toPlainString());
        }
        photos.forEach(p -> body.add("images", new FileSystemResource(p)));
        return send(restClient.post().uri(url(path)).contentType(MediaType.MULTIPART_FORM_DATA).body(body), path);
    }

    /** Mengirim permintaan dan mengembalikan jawaban berstatus success; selain itu {@link AiServiceException}. */
    private Map<?, ?> send(RestClient.RequestHeadersSpec<?> request, String path) throws AiServiceException {
        requireConfigured();
        Map<?, ?> response;
        try {
            response = request.retrieve().body(Map.class);
        } catch (RestClientResponseException ex) {
            throw answered(ex);
        } catch (RestClientException ex) {
            throw new AiServiceException("AI service unreachable: " + ex.getMessage(), ex);
        }
        if (response == null || !"success".equals(response.get("status"))) {
            throw new AiServiceException("AI service %s did not answer success: %s".formatted(path, response));
        }
        return response;
    }

    /** Untuk jawaban tanpa isi (DELETE layanan AI menjawab 204). */
    private void sendNoBody(RestClient.RequestHeadersSpec<?> request, String path) throws AiServiceException {
        requireConfigured();
        try {
            request.retrieve().toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw answered(ex);
        } catch (RestClientException ex) {
            throw new AiServiceException("AI service unreachable: " + ex.getMessage(), ex);
        }
        log.debug("AI service {}: done", path);
    }

    private void requireConfigured() throws AiServiceException {
        if (!configured()) {
            throw new AiServiceException("AI service URL is not configured (AI_SERVICE_URL)");
        }
    }

    private String url(String path) {
        return properties.baseUrl() + path;
    }

    private static AiServiceException answered(RestClientResponseException ex) {
        return new AiServiceException(
                "AI service answered %d: %s".formatted(ex.getStatusCode().value(), ex.getResponseBodyAsString()), ex);
    }

    private static Map<?, ?> result(Map<?, ?> response, String path) throws AiServiceException {
        if (response.get("result") instanceof Map<?, ?> result) {
            return result;
        }
        throw new AiServiceException("AI service %s answer has no result: %s".formatted(path, response));
    }

    private static double number(Map<?, ?> result, String field, String path) throws AiServiceException {
        if (result.get(field) instanceof Number n && Double.isFinite(n.doubleValue())) {
            return n.doubleValue();
        }
        throw new AiServiceException("AI service %s answer has no result.%s: %s".formatted(path, field, result));
    }

    private static FertilizationInterval interval(Object row) throws AiServiceException {
        if (row instanceof Map<?, ?> map
                && map.get("id") instanceof Number id
                && map.get("interval_days") instanceof Number days) {
            return new FertilizationInterval(id.longValue(), days.intValue());
        }
        throw new AiServiceException("AI service fertilization schedule row has no id/interval_days: " + row);
    }

    private static boolean isPhoto(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return PHOTO_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    private static FileTime modified(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (IOException ex) {
            return FileTime.fromMillis(0);
        }
    }
}
