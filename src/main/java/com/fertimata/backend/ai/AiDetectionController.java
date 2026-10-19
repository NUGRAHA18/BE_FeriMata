package com.fertimata.backend.ai;

import com.fertimata.backend.common.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
@Tag(name = "AI detections", description = "Results produced by the vision model on the edge device")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class AiDetectionController {

    private final AiDetectionService detectionService;

    public AiDetectionController(AiDetectionService detectionService) {
        this.detectionService = detectionService;
    }

    @GetMapping("/detections")
    @Operation(
            summary = "List AI detections",
            description =
                    "Labels and scores are reported by the model; the backend stores them without interpreting them. "
                            + "Filters are exclusive: stationCode, then deviceId, then detectionType.")
    public PageResponse<AiDetectionResponse> list(
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) String detectionType,
            @RequestParam(required = false) String stationCode,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        return detectionService.list(deviceId, detectionType, stationCode, PageRequest.of(page, size));
    }

    @GetMapping("/detections/{id}")
    @Operation(summary = "Get one AI detection")
    public AiDetectionResponse get(@PathVariable Long id) {
        return detectionService.get(id);
    }
}
