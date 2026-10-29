package com.fertimata.backend.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fertimata.backend.alert.AlertSeverity;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WebPushClientTest {

    // Pasangan kunci dari contoh RFC 8291; yang "UA" dipakai sebagai kunci yang tidak sepasang.
    static final String PUBLIC = "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
    static final String PRIVATE = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";
    private static final String OTHER_PRIVATE = "q1dXpw3UpT5VOmu_cf_v6ih07Aems3njxI-JWgLcM94";

    static PushProperties properties(boolean enabled, String publicKey, String privateKey, String subject) {
        return new PushProperties(
                enabled,
                publicKey,
                privateKey,
                subject,
                AlertSeverity.CRITICAL,
                Duration.ofHours(24),
                Duration.ofSeconds(10),
                List.of("fcm.googleapis.com", "push.services.mozilla.com", "push.apple.com"));
    }

    private static WebPushClient client(PushProperties properties) {
        return new WebPushClient(properties, Clock.systemUTC());
    }

    @Test
    @DisplayName("without PUSH_ENABLED nothing is loaded and no public key is offered")
    void disabled() {
        WebPushClient client = client(properties(false, null, null, null));

        assertThat(client.enabled()).isFalse();
        assertThat(client.publicKey()).isNull();
    }

    @Test
    @DisplayName("a valid key pair is accepted and its public key is offered to browsers")
    void validKeys() {
        WebPushClient client = client(properties(true, PUBLIC, PRIVATE, "mailto:ops@example.com"));

        assertThat(client.enabled()).isTrue();
        assertThat(client.publicKey()).isEqualTo(PUBLIC);
    }

    @Test
    @DisplayName("a public key that does not belong to the private key stops startup")
    void mismatchedKeysFailFast() {
        assertThatThrownBy(() -> client(properties(true, PUBLIC, OTHER_PRIVATE, "mailto:ops@example.com")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not belong");
    }

    @Test
    @DisplayName("missing keys or contact stop startup with a hint")
    void missingConfigurationFailsFast() {
        assertThatThrownBy(() -> client(properties(true, null, null, "mailto:ops@example.com")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("generate-vapid-keys");
        assertThatThrownBy(() -> client(properties(true, PUBLIC, PRIVATE, "ops@example.com")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("VAPID_SUBJECT");
    }

    @Test
    @DisplayName("only HTTPS endpoints of known push services are accepted")
    void endpointAllowList() {
        WebPushClient client = client(properties(false, null, null, null));

        assertThat(client.isAllowedEndpoint("https://fcm.googleapis.com/fcm/send/abc")).isTrue();
        assertThat(client.isAllowedEndpoint("https://updates.push.services.mozilla.com/wpush/v2/abc")).isTrue();
        assertThat(client.isAllowedEndpoint("https://web.push.apple.com/QGz")).isTrue();

        assertThat(client.isAllowedEndpoint("http://fcm.googleapis.com/fcm/send/abc")).isFalse();
        assertThat(client.isAllowedEndpoint("https://fcm.googleapis.com.attacker.example/x")).isFalse();
        assertThat(client.isAllowedEndpoint("https://evilfcm.googleapis.com.example/x")).isFalse();
        assertThat(client.isAllowedEndpoint("https://user@fcm.googleapis.com/x")).isFalse();
        assertThat(client.isAllowedEndpoint("https://localhost:8080/api/users")).isFalse();
        assertThat(client.isAllowedEndpoint("https://169.254.169.254/latest")).isFalse();
        assertThat(client.isAllowedEndpoint("not a url")).isFalse();
    }
}
