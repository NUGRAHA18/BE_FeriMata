package com.fertimata.backend.captures;

import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Serves the capture folder read-only at {@code /captures/**}; Spring's resource resolver blocks path escapes.
 * Requires the bearer token like the API, so the frontend fetches images instead of plain {@code <img src>}. */
@Configuration
public class CaptureFilesConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CaptureFilesConfig.class);

    private final CapturesProperties properties;

    public CaptureFilesConfig(CapturesProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (!properties.enabled()) {
            log.info("Capture files are not served: app.captures.directory is not set");
            return;
        }
        Path root = Path.of(properties.directory()).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            // Not fatal: on a fresh Jetson the edge agent creates the folder on its first patrol.
            log.warn("Capture directory {} does not exist yet; /captures will answer 404 until it does", root);
        }
        // Path.toUri only ends in "/" for a folder that exists; without it the location is a file.
        String location = root.toUri().toString();
        registry.addResourceHandler(CapturesProperties.URL_PREFIX + "/**")
                .addResourceLocations(location.endsWith("/") ? location : location + "/")
                .setCacheControl(CacheControl.maxAge(properties.cacheMaxAge()).cachePrivate());
        log.info("Serving capture files from {} at {}/**", root, CapturesProperties.URL_PREFIX);
    }
}
