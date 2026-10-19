package com.fertimata.backend.security.auth;

import com.fertimata.backend.common.exception.ApiException;
import com.fertimata.backend.common.exception.ErrorCode;
import com.fertimata.backend.security.jwt.JwtTokenService;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Reads the authenticated account out of the security context.
 *
 * <p>Services use this instead of taking a user id from the request body: a client must never be
 * able to claim it acted on behalf of somebody else.
 */
@Component
public class CurrentUserProvider {

    public Optional<Long> userId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtToken) {
            Jwt jwt = jwtToken.getToken();
            Object claim = jwt.getClaim(JwtTokenService.CLAIM_USER_ID);
            if (claim instanceof Number number) {
                return Optional.of(number.longValue());
            }
        }
        return Optional.empty();
    }

    public Optional<String> username() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        return Optional.ofNullable(authentication.getName());
    }

    public String requireUsername() {
        return username()
                .orElseThrow(() -> new ApiException(ErrorCode.AUTHENTICATION_FAILED, "No authenticated account"));
    }
}
