package com.fertimata.backend.common.exception;

import java.util.Map;

/** Thrown when a requested entity does not exist. Maps to HTTP 404. */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String resource, Object identifier) {
        super(
                ErrorCode.RESOURCE_NOT_FOUND,
                "%s not found: %s".formatted(resource, identifier),
                Map.of("resource", resource, "identifier", String.valueOf(identifier)));
    }
}
