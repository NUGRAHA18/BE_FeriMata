package com.fertimata.backend.cultivation;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Pengaturan siklus tanam.
 *
 * @param zone zona waktu untuk menentukan "hari ini" saat menghitung HST; greenhouse ada di WIB
 * @param defaultPhases template fase yang dipakai saat siklus baru dibuat tanpa fase. Angka-angkanya
 *     dari Arsitektur-Sistem.md §12; batas HST-nya placeholder sampai tim agronomi menetapkannya,
 *     dan operator bisa mengubahnya lewat API/UI kapan saja.
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
            BigDecimal ratioN,
            BigDecimal ratioP,
            BigDecimal ratioK,
            String notes) {}
}
