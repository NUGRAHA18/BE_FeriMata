package com.fertimata.backend.captures;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the edge agent's trolley photos are read from (keputusan D7).
 *
 * <p>The edge agent writes {@code <root>/YYYY-MM-DD/YYYYMMDD-HHMM_Snn_timur.png} on the Jetson and
 * reports {@code imageUrl = /captures/YYYY-MM-DD/...png}. The backend serves that folder read-only under
 * {@link #URL_PREFIX}, behind the same authentication as the API. In a container the folder is a
 * read-only volume.
 *
 * @param directory the edge agent's {@code camera.root}; empty disables the endpoint
 * @param cacheMaxAge how long a browser may cache a photo; a capture never changes once written
 */
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
