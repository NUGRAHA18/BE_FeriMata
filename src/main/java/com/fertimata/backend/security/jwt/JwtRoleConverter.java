package com.fertimata.backend.security.jwt;

import java.util.Collection;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/** Memetakan klaim {@code roles} ke authorities; konverter bawaan hanya membaca klaim {@code scope} OAuth2.
 * Token akses yang terbit sebelum peran ADMIN dihapus (V23) masih membawa "ADMIN"; dianggap OPERATOR sampai
 * kedaluwarsa (beberapa menit) supaya pemakainya tidak ditolak sebelum token diperbarui. */

public class JwtRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String LEGACY_ADMIN = "ADMIN";

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList(JwtTokenService.CLAIM_ROLES);
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        return roles.stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + (LEGACY_ADMIN.equals(role) ? "OPERATOR" : role)))
                .toList();
    }
}
