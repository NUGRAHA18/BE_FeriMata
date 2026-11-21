package com.fertimata.backend.user;

import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** OPERATOR (peran tertinggi: kendali alat, data tanaman, peringatan, dan kelola akun) > VIEWER (hanya baca);
 * hierarki ditegakkan {@code RoleHierarchy} di {@code SecurityConfig}. Peran ADMIN dihapus (V23): pemakainya
 * satu-dua petani di kebun yang sama, jadi kelola akun diwariskan ke OPERATOR. */
public enum Role {
    OPERATOR,
    VIEWER;

    /** Role names as used in security expressions, kept in one place to avoid typo-driven bugs. */
    public static final class Names {
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
