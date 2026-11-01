package com.fertimata.backend.config;

import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Hanya {@code normalSources} yang dianggap listrik normal; selain itu (BATTERY, UNKNOWN, nilai baru)
 * dianggap cadangan, pembacaan paling aman untuk interlock. Kosakata milik edge agent. */
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
