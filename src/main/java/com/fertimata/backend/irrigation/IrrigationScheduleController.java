package com.fertimata.backend.irrigation;

import com.fertimata.backend.irrigation.dto.IrrigationScheduleRequest;
import com.fertimata.backend.irrigation.dto.IrrigationScheduleResponse;
import com.fertimata.backend.irrigation.dto.IrrigationScheduleUpdateRequest;
import com.fertimata.backend.user.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/irrigation/schedules")
@Tag(name = "Irrigation schedule", description = "Daily watering times; the backend starts the irrigation pump at each time")
@SecurityRequirement(name = "bearerAuth")
public class IrrigationScheduleController {

    private final IrrigationScheduleService service;

    public IrrigationScheduleController(IrrigationScheduleService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List watering times, earliest first", description = "With the next start and the result of the last run.")
    public List<IrrigationScheduleResponse> list() {
        return service.list();
    }

    @PostMapping
    @PreAuthorize("hasRole('" + Role.Names.OPERATOR + "')")
    @Operation(
            summary = "Add a daily watering time",
            description = "time is farm time (WIB) HH:mm; durationSeconds at most the pump's maxRunSeconds. A time "
                    + "that has already passed today starts tomorrow. 409 for a duplicate time or a too long duration.")
    public ResponseEntity<IrrigationScheduleResponse> create(@Valid @RequestBody IrrigationScheduleRequest request) {
        IrrigationScheduleResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/irrigation/schedules/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('" + Role.Names.OPERATOR + "')")
    @Operation(summary = "Change a watering time, its duration, or switch it on/off")
    public IrrigationScheduleResponse update(@PathVariable Long id, @Valid @RequestBody IrrigationScheduleUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('" + Role.Names.OPERATOR + "')")
    @Operation(summary = "Delete a watering time")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
