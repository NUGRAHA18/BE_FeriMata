package com.fertimata.backend.device;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/devices")
@Tag(name = "Devices", description = "Edge device registry and liveness")
@SecurityRequirement(name = "bearerAuth")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @GetMapping
    @Operation(summary = "List registered devices")
    public List<DeviceResponse> list() {
        return deviceService.list();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one device")
    public DeviceResponse get(@PathVariable Long id) {
        return deviceService.get(id);
    }

    @GetMapping("/{id}/status")
    @Operation(
            summary = "Get device liveness",
            description = "Includes the configured offline timeout so the client does not hard-code it.")
    public DeviceStatusResponse status(@PathVariable Long id) {
        return deviceService.status(id);
    }

    @PostMapping
    @Operation(
            summary = "Register a device",
            description = "Devices may also appear automatically on first contact when auto-registration is enabled.")
    public ResponseEntity<DeviceResponse> register(@Valid @RequestBody DeviceRegistrationRequest request) {
        DeviceResponse created = deviceService.register(request);
        return ResponseEntity.created(URI.create("/api/devices/" + created.id())).body(created);
    }
}
