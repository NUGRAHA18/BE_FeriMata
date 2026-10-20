package com.fertimata.backend.cultivation;

import com.fertimata.backend.cultivation.dto.CycleStatusResponse;
import com.fertimata.backend.cultivation.dto.GrowthPhaseRequest;
import com.fertimata.backend.cultivation.dto.PlantingCycleRequest;
import com.fertimata.backend.cultivation.dto.PlantingCycleResponse;
import com.fertimata.backend.cultivation.dto.PlantingCycleUpdateRequest;
import com.fertimata.backend.user.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cultivation")
@Tag(name = "Cultivation", description = "Planting cycles, growth phases and days-after-transplant (HST)")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class CultivationController {

    private final CultivationService cultivationService;

    public CultivationController(CultivationService cultivationService) {
        this.cultivationService = cultivationService;
    }

    @GetMapping("/active")
    @Operation(
            summary = "Today's state of the active cycle",
            description = "HST, the phase in effect with its EC target and N:P:K ratio, and the next phase. 404 when no cycle is active.")
    public ResponseEntity<CycleStatusResponse> active() {
        return cultivationService.activeStatus().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/cycles")
    @Operation(summary = "List planting cycles, newest transplant first")
    public List<PlantingCycleResponse> list() {
        return cultivationService.list();
    }

    @GetMapping("/cycles/{id}")
    @Operation(summary = "Get one planting cycle with its phases")
    public PlantingCycleResponse get(@PathVariable Long id) {
        return cultivationService.get(id);
    }

    @GetMapping("/cycles/{id}/status")
    @Operation(summary = "Today's HST and phase for one cycle")
    public CycleStatusResponse status(@PathVariable Long id) {
        return cultivationService.status(id);
    }

    @PostMapping("/cycles")
    @PreAuthorize("hasRole('" + Role.Names.OPERATOR + "')")
    @Operation(
            summary = "Create a planting cycle",
            description = "Without phases the configured default template is used (Arsitektur-Sistem.md §12 ratios; HST boundaries are placeholders to edit).")
    public ResponseEntity<PlantingCycleResponse> create(@Valid @RequestBody PlantingCycleRequest request) {
        PlantingCycleResponse created = cultivationService.create(request);
        return ResponseEntity.created(URI.create("/api/cultivation/cycles/" + created.id())).body(created);
    }

    @PatchMapping("/cycles/{id}")
    @PreAuthorize("hasRole('" + Role.Names.OPERATOR + "')")
    @Operation(summary = "Update a cycle", description = "Setting status ACTIVE ends any other active cycle.")
    public PlantingCycleResponse update(@PathVariable Long id, @Valid @RequestBody PlantingCycleUpdateRequest request) {
        return cultivationService.update(id, request);
    }

    @PutMapping("/cycles/{id}/phases")
    @PreAuthorize("hasRole('" + Role.Names.OPERATOR + "')")
    @Operation(summary = "Replace the growth phases of a cycle", description = "Phases must not overlap; gaps are allowed.")
    public PlantingCycleResponse replacePhases(
            @PathVariable Long id, @RequestBody @NotEmpty List<@Valid GrowthPhaseRequest> phases) {
        return cultivationService.replacePhases(id, phases);
    }
}
