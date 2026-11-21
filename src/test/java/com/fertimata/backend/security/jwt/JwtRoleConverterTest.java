package com.fertimata.backend.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtRoleConverterTest {

    private final JwtRoleConverter converter = new JwtRoleConverter();

    private static Jwt token(Object roles) {
        Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "HS256").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        if (roles != null) {
            builder.claim(JwtTokenService.CLAIM_ROLES, roles);
        }
        return builder.claim("sub", "x").build();
    }

    @Test
    @DisplayName("roles become ROLE_ authorities")
    void mapsRoles() {
        assertThat(converter.convert(token(List.of("OPERATOR"))).stream().map(GrantedAuthority::getAuthority))
                .containsExactly("ROLE_OPERATOR");
        assertThat(converter.convert(token(List.of("VIEWER"))).stream().map(GrantedAuthority::getAuthority))
                .containsExactly("ROLE_VIEWER");
    }

    @Test
    @DisplayName("a token issued before ADMIN was removed still works as OPERATOR until it expires")
    void legacyAdminTokenIsOperator() {
        assertThat(converter.convert(token(List.of("ADMIN"))).stream().map(GrantedAuthority::getAuthority))
                .containsExactly("ROLE_OPERATOR");
    }

    @Test
    @DisplayName("no roles claim means no authorities")
    void noRoles() {
        assertThat(converter.convert(token(null))).isEmpty();
    }
}
