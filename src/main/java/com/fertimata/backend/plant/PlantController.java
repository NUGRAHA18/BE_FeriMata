package com.fertimata.backend.plant;

import com.fertimata.backend.plant.dto.PlantRequest;
import com.fertimata.backend.plant.dto.PlantResponse;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/plants")
@Tag(name = "Plants", description = "Plants shown on the dashboard")
@SecurityRequirement(name = "bearerAuth")
public class PlantController {

    private final PlantService plantService;

    public PlantController(PlantService plantService) {
        this.plantService = plantService;
    }

    @GetMapping
    @Operation(summary = "List plants by name", description = "active=true for the dashboard; omit for all.")
    public List<PlantResponse> list(@RequestParam(required = false) Boolean active) {
        return plantService.list(active);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one plant")
    public PlantResponse get(@PathVariable Long id) {
        return plantService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('" + Role.Names.OPERATOR + "')")
    @Operation(summary = "Add a plant", description = "Send the name the farmer typed; a unique code is made from it.")
    public ResponseEntity<PlantResponse> create(@Valid @RequestBody PlantRequest request) {
        PlantResponse created = plantService.create(request);
        return ResponseEntity.created(URI.create("/api/plants/" + created.id())).body(created);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('" + Role.Names.OPERATOR + "')")
    @Operation(
            summary = "Delete a plant",
            description = "AI detections that pointed at it are kept without the plant. Deleting the LAST plant also clears "
                    + "the transplant date (GET /api/cultivation/active becomes 404), so the next season starts again from "
                    + "adding the first plant.")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        plantService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
