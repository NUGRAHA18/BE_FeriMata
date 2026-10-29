package com.fertimata.backend.cultivation;

import com.fertimata.backend.cultivation.dto.CycleStatusResponse;
import com.fertimata.backend.cultivation.dto.TransplantDateRequest;
import com.fertimata.backend.user.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cultivation")
@Tag(name = "Cultivation", description = "Transplant date, days after transplant (HST) and growth phase")
@SecurityRequirement(name = "bearerAuth")
public class CultivationController {

    private final CultivationService cultivationService;

    public CultivationController(CultivationService cultivationService) {
        this.cultivationService = cultivationService;
    }

    @GetMapping("/active")
    @Operation(
            summary = "Today's HST and growth phase",
            description = "HST counted from the transplant date, the phase in effect with its EC target (phases follow the "
                    + "AI team's fuzzy), and the next phase. 404 when the transplant date is not set.")
    public ResponseEntity<CycleStatusResponse> active() {
        return cultivationService.activeStatus().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/transplant-date")
    @PreAuthorize("hasRole('" + Role.Names.OPERATOR + "')")
    @Operation(
            summary = "Set the greenhouse transplant date",
            description = "The only cultivation setting in the app: HST and the growth phase on the dashboard are counted "
                    + "from this date.")
    public CycleStatusResponse setTransplantDate(@Valid @RequestBody TransplantDateRequest request) {
        return cultivationService.setTransplantDate(request.transplantDate());
    }
}
