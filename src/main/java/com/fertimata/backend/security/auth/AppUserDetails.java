package com.fertimata.backend.security.auth;

import com.fertimata.backend.user.Role;
import com.fertimata.backend.user.User;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Adaptor antara entitas {@link User} dan Spring Security.
 *
 * <p>Menyimpan identifikasi akun (<i>account id</i>) sehingga kode pada tingkat bawah (misalnya 
 * audit perintah <i>actuator</i>) dapat mencatat pihak yang mengajukan permintaan aksi tanpa 
 * perlu melakukan pencarian ulang ke basis data.
 */

public class AppUserDetails implements UserDetails {

    private final Long userId;
    private final String username;
    private final String passwordHash;
    private final Role role;
    private final boolean enabled;

    public AppUserDetails(User user) {
        this.userId = user.getId();
        this.username = user.getUsername();
        this.passwordHash = user.getPasswordHash();
        this.role = user.getRole();
        this.enabled = user.isEnabled();
    }

    public Long userId() {
        return userId;
    }

    public Role role() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.copyOf(role.grantedAuthorities());
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public String toString() {
        return "AppUserDetails{userId=%s, username=%s, role=%s}".formatted(userId, username, role);
    }
}
