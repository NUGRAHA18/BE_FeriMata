package com.smartmelon.backend.config;

import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cara interpretasi sumber daya (power sources) yang dilaporkan.
 *
 * <p>Kosakata ini miliknya edge agent, jadi backend cuma perlu tahu nilai mana saja yang artinya
 * "normal supply". Selain itu — mau {@code BATTERY}, {@code UNKNOWN}, atau nilai yang belum pernah
 * kelihatan di build ini — bakal diangp jalan pakai backup, yang merupakan pembacaan paling aman
 * buat interlock.
 *
 * @param normalSources nilai power source yang menandakan lokasi lagi pakai normal supply
 */
@ConfigurationProperties(prefix = "app.power")
public record PowerProperties(List<String> normalSources) {

    public PowerProperties {
        normalSources = normalSources == null || normalSources.isEmpty()
                ? List.of("MAINS")
                : normalSources.stream().map(PowerProperties::normalise).toList();
    }

    public boolean isNormal(String source) {
        return source != null && normalSources.contains(normalise(source));
    }

    public static String normalise(String source) {
        return source == null ? null : source.trim().toUpperCase(Locale.ROOT);
    }
}
