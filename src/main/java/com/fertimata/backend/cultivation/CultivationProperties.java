package com.fertimata.backend.cultivation;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code zone}: zona "hari ini" untuk HST (WIB). {@code defaultPhases}: template fase siklus baru (Arsitektur-Sistem.md §12);
 * batas HST placeholder sampai ditetapkan tim agronomi, bisa diubah operator.
 */
@ConfigurationProperties(prefix = "app.cultivation")
public record CultivationProperties(ZoneId zone, List<PhaseTemplate> defaultPhases) {

    public CultivationProperties {
        zone = zone == null ? ZoneId.of("Asia/Jakarta") : zone;
        defaultPhases = defaultPhases == null ? List.of() : defaultPhases;
    }

    public record PhaseTemplate(
            String code,
            String name,
            int startHst,
            Integer endHst,
            BigDecimal ecTargetMin,
            BigDecimal ecTargetMax,
            String notes) {}
}
