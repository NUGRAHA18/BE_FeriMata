package com.fertimata.backend.plant;

import com.fertimata.backend.common.response.PageResponse;
import com.fertimata.backend.plant.dto.HarvestRequest;
import com.fertimata.backend.plant.dto.HarvestResponse;
import com.fertimata.backend.plant.dto.PlantObservationRequest;
import com.fertimata.backend.plant.dto.PlantObservationResponse;
import com.fertimata.backend.plant.dto.PlantRequest;
import com.fertimata.backend.plant.dto.PlantResponse;
import com.fertimata.backend.plant.dto.PlantUpdateRequest;
import com.fertimata.backend.security.auth.CurrentUserProvider;
import com.fertimata.backend.user.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
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
@RequestMapping("/api/plants")
@Tag(name = "Plants", description = "Treatment plants and their manual observations")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class PlantController {

    private final PlantService plantService;
    private final HarvestService harvestService;
    private final CurrentUserProvider currentUserProvider;

    public PlantController(
            PlantService plantService, HarvestService harvestService, CurrentUserProvider currentUserProvider) {
        this.plantService = plantService;
        this.harvestService = harvestService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    @Operation(summary = "List plants", description = "Every filter is optional and they combine.")
    public List<PlantResponse> list(
            @RequestParam(required = false) Long cycleId,
            @RequestParam(required = false) String treatmentCode,
            @RequestParam(required = false) String blockCode,
            @RequestParam(required = false) String stationCode,
            @RequestParam(required = false) String comparisonGroup,
            @RequestParam(required = false) Boolean active) {
        return plantService.list(new PlantFilter(cycleId, treatmentCode, blockCode, stationCode, comparisonGroup, active));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one plant")
    public PlantResponse get(@PathVariable Long id) {
        return plantService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('" + Role.Names.OPERATOR + "')")
    @Operation(summary = "Register a plant", description = "cycleId defaults to the active cycle.")
    public ResponseEntity<PlantResponse> create(@Valid @RequestBody PlantRequest request) {
        PlantResponse created = plantService.create(request);
        return ResponseEntity.created(URI.create("/api/plants/" + created.id())).body(created);
    }

    @PostMapping("/batch")
    @PreAuthorize("hasRole('" + Role.Names.OPERATOR + "')")
    @Operation(summary = "Register many plants at once", description = "All or nothing: one duplicate code rejects the whole batch.")
    public ResponseEntity<List<PlantResponse>> createBatch(@RequestBody @NotEmpty List<@Valid PlantRequest> requests) {
        return ResponseEntity.status(HttpStatus.CREATED).body(plantService.createBatch(requests));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('" + Role.Names.OPERATOR + "')")
    @Operation(summary = "Update a plant")
    public PlantResponse update(@PathVariable Long id, @Valid @RequestBody PlantUpdateRequest request) {
        return plantService.update(id, request);
    }

    @GetMapping("/{id}/observations")
    @Operation(summary = "Observation log of a plant, newest first")
    public PageResponse<PlantObservationResponse> observations(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        return plantService.observations(id, PageRequest.of(page, size));
    }

    @PostMapping("/{id}/observations")
    @PreAuthorize("hasRole('" + Role.Names.OPERATOR + "')")
    @Operation(
            summary = "Record an observation",
            description = "Protokol-Data.md §2: height, leaf count, leaf-3 size, visual notes. The plot's latest soil readings are attached automatically.")
    public ResponseEntity<PlantObservationResponse> observe(
            @PathVariable Long id, @Valid @RequestBody PlantObservationRequest request) {
        PlantObservationResponse created =
                plantService.addObservation(id, request, currentUserProvider.username().orElse(null));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}/harvests")
    @Operation(summary = "Harvested fruits of a plant, newest first")
    public List<HarvestResponse> harvests(@PathVariable Long id) {
        return harvestService.forPlant(id);
    }

    @PostMapping("/{id}/harvests")
    @PreAuthorize("hasRole('" + Role.Names.OPERATOR + "')")
    @Operation(
            summary = "Record a harvested fruit",
            description = "Protokol-Data.md §6: weight, °Brix, firmness, flesh thickness, dry matter. One record per fruit.")
    public ResponseEntity<HarvestResponse> harvest(@PathVariable Long id, @Valid @RequestBody HarvestRequest request) {
        HarvestResponse created = harvestService.add(id, request, currentUserProvider.username().orElse(null));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
