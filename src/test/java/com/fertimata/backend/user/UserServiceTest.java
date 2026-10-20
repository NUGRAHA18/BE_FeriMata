package com.fertimata.backend.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fertimata.backend.common.exception.BusinessRuleException;
import com.fertimata.backend.security.auth.RefreshTokenService;
import com.fertimata.backend.support.TestFixtures;
import com.fertimata.backend.user.dto.ResetPasswordRequest;
import com.fertimata.backend.user.dto.UserCreateRequest;
import com.fertimata.backend.user.dto.UserUpdateRequest;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    private UserService service;
    private User admin;
    private User operator;

    @BeforeEach
    void setUp() {
        service = new UserService(userRepository, passwordEncoder, refreshTokenService);
        admin = new User("admin", "hash", "Admin", Role.ADMIN);
        TestFixtures.setId(admin, 1L);
        operator = TestFixtures.operator(2L, "operator");
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findById(2L)).thenReturn(Optional.of(operator));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            TestFixtures.setId(u, 9L);
            return u;
        });
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$encoded");
    }

    @Test
    @DisplayName("a new account stores an encoded password and must change it at first sign-in")
    void createEncodesPassword() {
        when(userRepository.existsByUsername("pak.budi")).thenReturn(false);

        UserResponse created = service.create(new UserCreateRequest("pak.budi", "rahasia123", "Budi", Role.VIEWER, null), "admin");

        assertThat(created.username()).isEqualTo("pak.budi");
        assertThat(created.role()).isEqualTo(Role.VIEWER);
        assertThat(created.mustChangePassword()).isTrue();
        assertThat(created.toString()).doesNotContain("rahasia123");
    }

    @Test
    @DisplayName("an admin cannot disable or demote their own account")
    void selfProtection() {
        assertThatThrownBy(() -> service.update(1L, new UserUpdateRequest(null, null, false), "admin"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("sendiri");
        assertThatThrownBy(() -> service.update(1L, new UserUpdateRequest(null, Role.OPERATOR, null), "admin"))
                .isInstanceOf(BusinessRuleException.class);
        verify(refreshTokenService, never()).revokeAllForUser(any());
    }

    @Test
    @DisplayName("the last active admin cannot be demoted by another admin")
    void lastAdminProtected() {
        when(userRepository.countByRoleAndEnabledTrue(Role.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> service.update(1L, new UserUpdateRequest(null, Role.OPERATOR, null), "other-admin"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("admin aktif");
    }

    @Test
    @DisplayName("disabling or changing a role ends every session of that account")
    void updateRevokesSessions() {
        UserResponse disabled = service.update(2L, new UserUpdateRequest("Operator Lapangan", null, false), "admin");

        assertThat(disabled.enabled()).isFalse();
        assertThat(disabled.fullName()).isEqualTo("Operator Lapangan");
        verify(refreshTokenService).revokeAllForUser(2L);
    }

    @Test
    @DisplayName("a password reset flags the account and ends its sessions")
    void resetPassword() {
        UserResponse reset = service.resetPassword(2L, new ResetPasswordRequest("sementara123"), "admin");

        assertThat(reset.mustChangePassword()).isTrue();
        assertThat(operator.getPasswordHash()).isEqualTo("$2a$encoded");
        verify(refreshTokenService).revokeAllForUser(2L);
    }
}
