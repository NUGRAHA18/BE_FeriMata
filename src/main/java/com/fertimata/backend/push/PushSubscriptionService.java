package com.fertimata.backend.push;

import com.fertimata.backend.common.exception.BusinessRuleException;
import com.fertimata.backend.push.dto.PushSubscriptionRequest;
import com.fertimata.backend.user.User;
import com.fertimata.backend.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Menyimpan dan merawat langganan push. Setiap method satu transaksi pendek. */
@Service
public class PushSubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(PushSubscriptionService.class);

    /** Data yang dibutuhkan untuk mengirim, dilepas dari entity supaya aman dipakai di thread lain. */
    public record Target(Long id, String endpoint, String p256dh, String auth) {}

    private final PushSubscriptionRepository repository;
    private final UserRepository userRepository;
    private final WebPushClient client;

    public PushSubscriptionService(
            PushSubscriptionRepository repository, UserRepository userRepository, WebPushClient client) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.client = client;
    }

    /**
     * Mendaftarkan browser ini untuk akun yang sedang login. Endpoint yang sama didaftarkan ulang
     * (mis. HP yang dipakai bergantian) berpindah ke akun terakhir dengan kunci terbaru.
     *
     * @return true bila langganan baru dibuat
     */
    @Transactional
    public boolean subscribe(String username, PushSubscriptionRequest request, String userAgent) {
        if (!client.enabled()) {
            throw new BusinessRuleException("Push notifications are not configured on the server");
        }
        String endpoint = request.endpoint().trim();
        if (!client.isAllowedEndpoint(endpoint)) {
            throw new BusinessRuleException(
                    "The endpoint is not a known browser push service", Map.of("endpoint", "not allowed"));
        }
        requireKeyLength(request.keys().p256dh(), 65, "p256dh");
        requireKeyLength(request.keys().auth(), 16, "auth");

        User user = userRepository
                .findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Signed-in user not found: " + username));
        PushSubscription subscription = repository.findByEndpoint(endpoint).orElse(null);
        boolean created = subscription == null;
        if (created) {
            subscription = new PushSubscription();
            subscription.setEndpoint(endpoint);
        }
        subscription.setUser(user);
        subscription.setP256dh(request.keys().p256dh().trim());
        subscription.setAuth(request.keys().auth().trim());
        subscription.setUserAgent(truncate(userAgent, 255));
        subscription.setFailureCount(0);
        repository.save(subscription);
        if (created) {
            log.info("Push subscription added for {}", username);
        }
        return created;
    }

    /** Menghapus langganan milik akun ini; endpoint milik akun lain atau yang tidak ada diabaikan. */
    @Transactional
    public void unsubscribe(String username, String endpoint) {
        repository.findByEndpoint(endpoint.trim())
                .filter(s -> s.getUser().getUsername().equals(username))
                .ifPresent(s -> {
                    repository.delete(s);
                    log.info("Push subscription removed by {}", username);
                });
    }

    @Transactional(readOnly = true)
    public List<Target> recipients() {
        return repository.findAllOfEnabledUsers().stream().map(PushSubscriptionService::target).toList();
    }

    @Transactional(readOnly = true)
    public List<Target> forUser(String username) {
        return userRepository.findByUsername(username)
                .map(user -> repository.findByUserId(user.getId()).stream().map(PushSubscriptionService::target).toList())
                .orElse(List.of());
    }

    @Transactional(readOnly = true)
    public long countForUser(String username) {
        return userRepository.findByUsername(username).map(user -> repository.countByUserId(user.getId())).orElse(0L);
    }

    @Transactional
    public void recordDelivered(Long id, Instant at) {
        repository.findById(id).ifPresent(s -> {
            s.setLastSuccessAt(at);
            s.setFailureCount(0);
        });
    }

    @Transactional
    public void recordFailed(Long id) {
        repository.findById(id).ifPresent(s -> s.setFailureCount(s.getFailureCount() + 1));
    }

    @Transactional
    public void remove(Long id) {
        repository.findById(id).ifPresent(repository::delete);
    }

    private static Target target(PushSubscription s) {
        return new Target(s.getId(), s.getEndpoint(), s.getP256dh(), s.getAuth());
    }

    private static void requireKeyLength(String value, int bytes, String field) {
        int length;
        try {
            length = EcKeys.decodeBase64Url(value).length;
        } catch (IllegalArgumentException ex) {
            length = -1;
        }
        if (length != bytes) {
            throw new BusinessRuleException(
                    "Subscription key %s must be %d bytes of base64url".formatted(field, bytes),
                    Map.of("keys." + field, "invalid"));
        }
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
