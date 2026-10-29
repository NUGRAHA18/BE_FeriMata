package com.fertimata.backend.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fertimata.backend.common.exception.BusinessRuleException;
import com.fertimata.backend.push.dto.PushSubscriptionRequest;
import com.fertimata.backend.support.TestFixtures;
import com.fertimata.backend.user.User;
import com.fertimata.backend.user.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PushSubscriptionServiceTest {

    private static final String ENDPOINT = "https://fcm.googleapis.com/fcm/send/abc";
    // p256dh 65 byte dan auth 16 byte dari contoh RFC 8291.
    private static final String P256DH =
            "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    private static final String AUTH = "BTBZMqHH6r4Tts7J_aSIgg";

    @Mock
    private PushSubscriptionRepository repository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WebPushClient client;

    private PushSubscriptionService service;

    @BeforeEach
    void setUp() {
        service = new PushSubscriptionService(repository, userRepository, client);
    }

    private static PushSubscriptionRequest request(String endpoint, String p256dh, String auth) {
        return new PushSubscriptionRequest(endpoint, new PushSubscriptionRequest.Keys(p256dh, auth));
    }

    @Test
    @DisplayName("subscribing is refused while the server has no VAPID keys")
    void refusedWhenDisabled() {
        when(client.enabled()).thenReturn(false);

        assertThatThrownBy(() -> service.subscribe("operator", request(ENDPOINT, P256DH, AUTH), null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    @DisplayName("an endpoint outside the known push services is refused")
    void unknownEndpointIsRefused() {
        when(client.enabled()).thenReturn(true);
        when(client.isAllowedEndpoint("https://intranet.local/hook")).thenReturn(false);

        assertThatThrownBy(() -> service.subscribe("operator", request("https://intranet.local/hook", P256DH, AUTH), null))
                .isInstanceOf(BusinessRuleException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("keys of the wrong length are refused before anything is stored")
    void malformedKeysAreRefused() {
        when(client.enabled()).thenReturn(true);
        when(client.isAllowedEndpoint(ENDPOINT)).thenReturn(true);

        assertThatThrownBy(() -> service.subscribe("operator", request(ENDPOINT, AUTH, AUTH), null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("p256dh");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("a new browser is stored for the signed-in account")
    void newSubscriptionIsStored() {
        when(client.enabled()).thenReturn(true);
        when(client.isAllowedEndpoint(ENDPOINT)).thenReturn(true);
        User operator = TestFixtures.operator(3L, "operator");
        when(userRepository.findByUsername("operator")).thenReturn(Optional.of(operator));
        when(repository.findByEndpoint(ENDPOINT)).thenReturn(Optional.empty());

        boolean created = service.subscribe("operator", request(ENDPOINT, P256DH, AUTH), "Mozilla/5.0 (Linux; Android 14)");

        assertThat(created).isTrue();
        ArgumentCaptor<PushSubscription> saved = ArgumentCaptor.forClass(PushSubscription.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getUser()).isSameAs(operator);
        assertThat(saved.getValue().getP256dh()).isEqualTo(P256DH);
    }

    @Test
    @DisplayName("a shared phone that signs in with another account moves its subscription to that account")
    void existingEndpointMovesToNewAccount() {
        when(client.enabled()).thenReturn(true);
        when(client.isAllowedEndpoint(ENDPOINT)).thenReturn(true);
        User viewer = TestFixtures.operator(4L, "viewer");
        when(userRepository.findByUsername("viewer")).thenReturn(Optional.of(viewer));
        PushSubscription existing = new PushSubscription();
        existing.setEndpoint(ENDPOINT);
        existing.setUser(TestFixtures.operator(3L, "operator"));
        existing.setFailureCount(4);
        when(repository.findByEndpoint(ENDPOINT)).thenReturn(Optional.of(existing));

        boolean created = service.subscribe("viewer", request(ENDPOINT, P256DH, AUTH), null);

        assertThat(created).isFalse();
        assertThat(existing.getUser()).isSameAs(viewer);
        assertThat(existing.getFailureCount()).isZero();
    }

    @Test
    @DisplayName("an account cannot unsubscribe another account's browser")
    void unsubscribeOnlyOwnBrowser() {
        PushSubscription other = new PushSubscription();
        other.setUser(TestFixtures.operator(3L, "operator"));
        when(repository.findByEndpoint(ENDPOINT)).thenReturn(Optional.of(other));

        service.unsubscribe("viewer", ENDPOINT);

        verify(repository, never()).delete(any());
    }
}
