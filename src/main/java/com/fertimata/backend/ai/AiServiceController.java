package com.fertimata.backend.ai;

import com.fertimata.backend.common.exception.ApiException;
import com.fertimata.backend.common.exception.ErrorCode;
import com.fertimata.backend.common.exception.InvalidCommandException;
import com.fertimata.backend.user.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Endpoint layanan AI yang diteruskan backend: status, riwayat inferensi AI, dan deteksi YOLO. */
@RestController
@RequestMapping("/api/ai")
@Tag(name = "AI service", description = "Pass-through to the AI team's service (AI_SERVICE_URL)")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class AiServiceController {

    private static final int MAX_PHOTOS = 10;

    /** Indikator "AI berjalan" di aplikasi dipanggil berkala dari banyak layar; satu cek ke AI per jangka ini. */
    static final Duration STATUS_CACHE = Duration.ofSeconds(15);

    private final AtomicReference<Status> lastStatus = new AtomicReference<>();

    private final AiServiceClient client;
    private final Clock clock;

    public AiServiceController(AiServiceClient client, Clock clock) {
        this.client = client;
        this.clock = clock;
    }

    @Schema(name = "AiServiceStatus")
    public record Status(
            @Schema(description = "AI_SERVICE_URL is set") boolean configured,
            @Schema(description = "GET /health answered") boolean reachable,
            @Schema(example = "Running Melon AI System API") String message,
            @Schema(description = "Round trip of the health check; null when not reachable") Long latencyMs,
            Instant checkedAt) {}

    @Schema(name = "AiDetectionRun")
    public record DetectionRun(
            @Schema(example = "deficiency") String kind,
            @Schema(description = "Photos sent: uploaded ones, else the newest patrol photos") int photos,
            @Schema(description = "As answered by the AI: deficiency = percent per class, disease = list of labels")
                    Object result) {}

    @GetMapping("/status")
    @Operation(
            summary = "Is the AI service up?",
            description = "Calls GET /health of the AI service (5 s timeout); the answer is reused for 15 s "
                    + "(see checkedAt). Never fails; check reachable.")
    public Status status() {
        Status cached = lastStatus.get();
        if (cached != null && Duration.between(cached.checkedAt(), clock.instant()).compareTo(STATUS_CACHE) < 0) {
            return cached;
        }
        Status fresh = check();
        lastStatus.set(fresh);
        return fresh;
    }

    private Status check() {
        Instant start = clock.instant();
        if (!client.configured()) {
            return new Status(false, false, "AI_SERVICE_URL is empty", null, start);
        }
        try {
            String message = client.health();
            long latency = Duration.between(start, clock.instant()).toMillis();
            return new Status(true, true, message, latency, start);
        } catch (AiServiceException ex) {
            return new Status(true, false, ex.getMessage(), null, start);
        }
    }

    @GetMapping("/history/{kind}")
    @Operation(
            summary = "Inference history kept by the AI service",
            description = "kind = irrigation or fertilization. Rows as stored by the AI (newest first): every "
                    + "run_irrigation/run_fertilization call is recorded there, including ones the backend did not "
                    + "execute. 502 when the AI service cannot be used.")
    public List<Map<String, Object>> history(
            @PathVariable String kind, @RequestParam(defaultValue = "30") @Min(1) @Max(500) int limit) {
        AiServiceClient.HistoryKind historyKind = parse(AiServiceClient.HistoryKind.class, kind);
        try {
            return client.history(historyKind, limit);
        } catch (AiServiceException ex) {
            throw unavailable(ex);
        }
    }

    @PostMapping(value = "/detect/{kind}", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.ALL_VALUE})
    @PreAuthorize("hasRole('" + Role.Names.OPERATOR + "')")
    @Operation(
            summary = "Run the AI vision model on leaf photos",
            description = "kind = deficiency or disease. Upload up to 10 photos as multipart 'images'; without uploads "
                    + "the newest patrol photos are used. 502 when the AI service cannot be used.")
    public DetectionRun detect(
            @PathVariable String kind, @RequestPart(name = "images", required = false) List<MultipartFile> images) {
        AiServiceClient.DetectionKind detectionKind = parse(AiServiceClient.DetectionKind.class, kind);
        List<Resource> photos = new ArrayList<>();
        if (images != null) {
            if (images.size() > MAX_PHOTOS) {
                throw new InvalidCommandException("At most %d photos".formatted(MAX_PHOTOS));
            }
            for (MultipartFile image : images) {
                photos.add(resource(image));
            }
        }
        if (photos.isEmpty()) {
            client.recentPhotos().forEach(p -> photos.add(new FileSystemResource(p)));
        }
        if (photos.isEmpty()) {
            throw new InvalidCommandException("No photo uploaded and no recent patrol photo found");
        }
        try {
            return new DetectionRun(detectionKind.name().toLowerCase(Locale.ROOT), photos.size(),
                    client.detect(detectionKind, photos));
        } catch (AiServiceException ex) {
            throw unavailable(ex);
        }
    }

    private static Resource resource(MultipartFile image) {
        try {
            String name = image.getOriginalFilename() == null ? "photo.jpg" : image.getOriginalFilename();
            return new ByteArrayResource(image.getBytes()) {
                @Override
                public String getFilename() {
                    return name;
                }
            };
        } catch (IOException ex) {
            throw new InvalidCommandException("Could not read uploaded photo: " + ex.getMessage());
        }
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new InvalidCommandException("Unknown kind '%s'".formatted(value));
        }
    }

    static ApiException unavailable(AiServiceException ex) {
        return new ApiException(ErrorCode.AI_SERVICE_ERROR, ex.getMessage(), ex);
    }
}
