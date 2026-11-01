package com.fertimata.backend.device;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Operator registration; status and last-seen are observed by the backend, never client-supplied. */
@Schema(name = "DeviceRegistrationRequest")
public record DeviceRegistrationRequest(
        @NotBlank
                @Size(max = 64)
                @Pattern(
                        regexp = "[A-Za-z0-9._-]+",
                        message = "may only contain letters, digits, dot, underscore and hyphen")
                @Schema(example = "JETSON-001")
                String deviceCode,
        @NotBlank @Size(max = 128) @Schema(example = "Greenhouse edge device") String name,
        @Size(max = 64) @Schema(example = "EDGE_GATEWAY") String type,
        @Size(max = 255) String description) {}
