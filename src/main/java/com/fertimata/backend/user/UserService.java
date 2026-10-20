package com.fertimata.backend.user;

import com.fertimata.backend.common.exception.BusinessRuleException;
import com.fertimata.backend.common.exception.ResourceNotFoundException;
import com.fertimata.backend.security.auth.RefreshTokenService;
import com.fertimata.backend.user.dto.ResetPasswordRequest;
import com.fertimata.backend.user.dto.UserCreateRequest;
import com.fertimata.backend.user.dto.UserUpdateRequest;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pengelolaan akun oleh ADMIN.
 *
 * <p>Dua pagar pengaman supaya admin tidak mengunci dirinya sendiri: akun yang sedang dipakai tidak
 * boleh dinonaktifkan atau diturunkan perannya, dan ADMIN terakhir yang aktif tidak boleh
 * dihilangkan.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return userRepository.findAllByOrderByUsernameAsc().stream().map(UserResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return UserResponse.from(require(id));
    }

    @Transactional
    public UserResponse create(UserCreateRequest request, String actor) {
        String username = request.username().trim();
        if (userRepository.existsByUsername(username)) {
            throw new BusinessRuleException(
                    "Nama pengguna %s sudah dipakai".formatted(username), Map.of("username", username));
        }
        User user = new User(username, passwordEncoder.encode(request.password()), blankToNull(request.fullName()), request.role());
        user.setMustChangePassword(request.mustChangePassword() == null || request.mustChangePassword());
        User saved = userRepository.save(user);
        log.info("User {} ({}) created by {}", saved.getUsername(), saved.getRole(), actor);
        return UserResponse.from(saved);
    }

    @Transactional
    public UserResponse update(Long id, UserUpdateRequest request, String actor) {
        User user = require(id);
        boolean self = user.getUsername().equals(actor);
        boolean sessionsInvalid = false;

        if (request.fullName() != null) {
            user.setFullName(blankToNull(request.fullName()));
        }
        if (request.role() != null && request.role() != user.getRole()) {
            if (self) {
                throw new BusinessRuleException("Anda tidak bisa mengubah peran akun Anda sendiri");
            }
            if (user.getRole() == Role.ADMIN) {
                ensureAnotherActiveAdmin(user);
            }
            user.setRole(request.role());
            sessionsInvalid = true;
        }
        if (request.enabled() != null && request.enabled() != user.isEnabled()) {
            if (self) {
                throw new BusinessRuleException("Anda tidak bisa menonaktifkan akun Anda sendiri");
            }
            if (!request.enabled() && user.getRole() == Role.ADMIN) {
                ensureAnotherActiveAdmin(user);
            }
            user.setEnabled(request.enabled());
            sessionsInvalid = sessionsInvalid || !request.enabled();
        }
        if (sessionsInvalid) {
            refreshTokenService.revokeAllForUser(user.getId());
        }
        log.info("User {} updated by {}", user.getUsername(), actor);
        return UserResponse.from(user);
    }

    /** Reset sandi oleh admin: pengguna harus menggantinya saat masuk, dan semua sesinya berakhir. */
    @Transactional
    public UserResponse resetPassword(Long id, ResetPasswordRequest request, String actor) {
        User user = require(id);
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(true);
        refreshTokenService.revokeAllForUser(user.getId());
        log.info("Password of {} reset by {}", user.getUsername(), actor);
        return UserResponse.from(user);
    }

    private void ensureAnotherActiveAdmin(User besides) {
        long others = userRepository.countByRoleAndEnabledTrue(Role.ADMIN) - (besides.isEnabled() ? 1 : 0);
        if (others <= 0) {
            throw new BusinessRuleException("Harus ada minimal satu admin aktif lain sebelum mengubah akun ini");
        }
    }

    private User require(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
