package com.fertimata.backend.ai;

import com.fertimata.backend.captures.CapturesProperties;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
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
 * Memanggil layanan AI tim AI. Kode AI tidak diubah: backend mengikuti form di Swagger mereka
 * ({@code Ai-code/app/docs/swagger_docs.py}).
 */
@Component
public class AiServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AiServiceClient.class);
    static final String RUN_IRRIGATION = "/api/fuzzy_sys/run_irrigation";
    private static final List<String> PHOTO_EXTENSIONS = List.of(".jpg", ".jpeg", ".png");
    private static final int PHOTO_SEARCH_DEPTH = 6;

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
     */
    public double irrigationSeconds(Map<String, BigDecimal> form) throws AiServiceException {
        if (!configured()) {
            throw new AiServiceException("AI service URL is not configured (AI_SERVICE_URL)");
        }
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        for (Map.Entry<String, BigDecimal> field : form.entrySet()) {
            if (field.getValue() == null) {
                throw new AiServiceException("Irrigation field " + field.getKey() + " is empty");
            }
            body.add(field.getKey(), field.getValue().toPlainString());
        }
        List<Path> photos = recentPhotos();
        photos.forEach(p -> body.add("images", new FileSystemResource(p)));

        Map<?, ?> response;
        try {
            response = restClient
                    .post()
                    .uri(properties.baseUrl() + RUN_IRRIGATION)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientResponseException ex) {
            throw new AiServiceException(
                    "AI service answered %d: %s".formatted(ex.getStatusCode().value(), ex.getResponseBodyAsString()),
                    ex);
        } catch (RestClientException ex) {
            throw new AiServiceException("AI service unreachable: " + ex.getMessage(), ex);
        }
        double seconds = durasi(response);
        log.info("AI fuzzy irrigation: {} s ({} photo(s) sent)", seconds, photos.size());
        return seconds;
    }

    private static double durasi(Map<?, ?> response) throws AiServiceException {
        if (response != null
                && "success".equals(response.get("status"))
                && response.get("result") instanceof Map<?, ?> result
                && result.get("durasi_irigasi") instanceof Number n
                && Double.isFinite(n.doubleValue())) {
            return n.doubleValue();
        }
        throw new AiServiceException("AI service answer has no result.durasi_irigasi: " + response);
    }

    /** Foto patroli terbaru dari folder edge agent (nama file bebas, dicari sampai beberapa subfolder). */
    List<Path> recentPhotos() {
        if (!captures.enabled() || properties.irrigationPhotos() <= 0) {
            return List.of();
        }
        Path root = Path.of(captures.directory());
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        Instant oldest = clock.instant().minus(properties.photoMaxAge());
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
