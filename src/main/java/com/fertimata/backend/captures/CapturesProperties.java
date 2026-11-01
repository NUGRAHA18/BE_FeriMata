package com.fertimata.backend.captures;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Trolley photos (decision D7) written by the edge to {@code <root>/YYYY-MM-DD/...png}, served read-only under
 * {@link #URL_PREFIX} with API auth; empty {@code directory} disables it; photos never change, so cacheable. */
@ConfigurationProperties(prefix = "app.captures")
public record CapturesProperties(String directory, Duration cacheMaxAge) {

    public static final String URL_PREFIX = "/captures";

    public CapturesProperties {
        directory = directory == null || directory.isBlank() ? null : directory.trim();
        cacheMaxAge = cacheMaxAge == null ? Duration.ofDays(1) : cacheMaxAge;
    }

    public boolean enabled() {
        return directory != null;
    }
}
