package com.fertimata.backend.actuator;

import com.fertimata.backend.actuator.dto.ActuatorCommandRequest;
import com.fertimata.backend.actuator.dto.ActuatorCommandResponse;
import com.fertimata.backend.actuator.dto.ActuatorRegistrationRequest;
import com.fertimata.backend.actuator.dto.ActuatorResponse;
import com.fertimata.backend.actuator.dto.ActuatorStatusResponse;
import com.fertimata.backend.actuator.dto.ActuatorUpdateRequest;
import com.fertimata.backend.common.response.ApiError;
import com.fertimata.backend.common.response.PageResponse;
import com.fertimata.backend.security.auth.CurrentUserProvider;
import com.fertimata.backend.user.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/actuators")
@Tag(name = "Actuators", description = "Controllable outputs and their command history")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class ActuatorController {

    private final ActuatorService actuatorService;
    private final ActuatorCommandService commandService;
    private final CurrentUserProvider currentUserProvider;

    public ActuatorController(
            ActuatorService actuatorService,
            ActuatorCommandService commandService,
            CurrentUserProvider currentUserProvider) {
        this.actuatorService = actuatorService;
        this.commandService = commandService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    @Operation(summary = "List actuators")
    public List<ActuatorResponse> list(@RequestParam(required = false) Long deviceId) {
        return deviceId == null ? actuatorService.list() : actuatorService.listByDevice(deviceId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one actuator")
    public ActuatorResponse get(@PathVariable Long id) {
        return actuatorService.get(id);
    }

    @GetMapping("/{id}/status")
    @Operation(
            summary = "Get the last state the device reported for an actuator",
            description = "Null until the device reports one; the backend does not infer state from commands.")
    public ActuatorStatusResponse status(@PathVariable Long id) {
        return actuatorService.status(id);
    }

    @PostMapping
    @Operation(summary = "Register an actuator")
    public ResponseEntity<ActuatorResponse> register(@Valid @RequestBody ActuatorRegistrationRequest request) {
        ActuatorResponse created = actuatorService.register(request);
        return ResponseEntity.created(URI.create("/api/actuators/" + created.id()))
                .body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('" + Role.Names.OPERATOR + "')")
    @Operation(
            summary = "Update an actuator",
            description = "Rename, enable or disable an output, or change its run-time limit (0 removes it).")
    public ActuatorResponse update(@PathVariable Long id, @Valid @RequestBody ActuatorUpdateRequest request) {
        return actuatorService.update(id, request);
    }

    @PostMapping("/{id}/commands")
    @PreAuthorize("hasRole('" + Role.Names.OPERATOR + "')")
    @Operation(
            summary = "Command an actuator",
            description =
                    """
                    Persists an audit record, then publishes the command to the device. A 202 means the
                    command reached the messaging layer, not that the hardware acted on it - watch the
                    command status, which only becomes EXECUTED when the device acknowledges it.
                    """)
    @ApiResponse(responseCode = "202", description = "Command accepted and published")
    @ApiResponse(
            responseCode = "400",
            description = "Actuator disabled or command invalid",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(
            responseCode = "503",
            description = "Messaging infrastructure unavailable; the attempt is still recorded as FAILED",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<ActuatorCommandResponse> issueCommand(
            @PathVariable Long id, @Valid @RequestBody ActuatorCommandRequest request) {
        ActuatorCommandResponse response =
                commandService.issueOperatorCommand(id, request, currentUserProvider.requireUsername());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/{id}/commands")
    @Operation(summary = "List the command history of an actuator")
    public PageResponse<ActuatorCommandResponse> commandHistory(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        return commandService.history(id, PageRequest.of(page, size));
    }
}
