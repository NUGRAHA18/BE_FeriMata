package com.fertimata.backend.security.jwt;

import java.util.Collection;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Memetakan klaim (<i>claim</i>) {@code roles} dari token akses ke dalam wewenang (<i>authorities</i>) Spring Security.
 *
 * <p>Tanpa pemetaan ini, pengonversi bawaan hanya akan memeriksa klaim {@code scope} OAuth2,
 * yang tidak diterbitkan oleh aplikasi ini.
 */

public class JwtRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList(JwtTokenService.CLAIM_ROLES);
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        return roles.stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }
}
