package com.fertimata.backend.user;

import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** ADMIN (+ kelola akun) > OPERATOR (kendali alat, data tanaman, peringatan) > VIEWER (hanya baca);
 * hierarki ditegakkan {@code RoleHierarchy} di {@code SecurityConfig}. */
public enum Role {
    ADMIN,
    OPERATOR,
    VIEWER;

    /** Role names as used in security expressions, kept in one place to avoid typo-driven bugs. */
    public static final class Names {
        public static final String ADMIN = "ADMIN";
        public static final String OPERATOR = "OPERATOR";
        public static final String VIEWER = "VIEWER";

        private Names() {}
    }

    /** Spring Security expects role authorities to carry the {@code ROLE_} prefix. */
    public String authority() {
        return "ROLE_" + name();
    }

    public List<GrantedAuthority> grantedAuthorities() {
        return List.of(new SimpleGrantedAuthority(authority()));
    }
}
