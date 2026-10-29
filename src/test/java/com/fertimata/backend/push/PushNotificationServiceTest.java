package com.fertimata.backend.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fertimata.backend.alert.AlertRaisedEvent;
import com.fertimata.backend.alert.AlertResponse;
import com.fertimata.backend.alert.AlertSeverity;
import com.fertimata.backend.alert.AlertSource;
import com.fertimata.backend.common.util.JsonSupport;
import com.fertimata.backend.push.dto.PushTestResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PushNotificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-29T03:00:00Z");

    @Mock
    private WebPushClient client;

    @Mock
    private PushSubscriptionService subscriptions;

    @Mock
    private JsonSupport jsonSupport;

    private PushNotificationService service;

    @BeforeEach
    void setUp() {
        service = new PushNotificationService(
                client,
                subscriptions,
                WebPushClientTest.properties(true, null, null, null),
                jsonSupport,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static AlertResponse alert(AlertSeverity severity) {
        return new AlertResponse(
                41L, "POWER_BACKUP", severity, "Listrik PLN padam", "Alat berjalan dengan baterai cadangan.",
                AlertSource.SYSTEM, 1L, "RIO-TANK-01", null, null, null, null, false, null, null, NOW, null);
    }

    @Test
    @DisplayName("alerts below the configured severity are not pushed")
    void belowThresholdIsIgnored() {
        when(client.enabled()).thenReturn(true);

        service.onAlertRaised(new AlertRaisedEvent(alert(AlertSeverity.WARNING)));

        verifyNoInteractions(subscriptions);
    }

    @Test
    @DisplayName("nothing happens when the server has no VAPID keys")
    void disabledIsIgnored() {
        when(client.enabled()).thenReturn(false);

        service.onAlertRaised(new AlertRaisedEvent(alert(AlertSeverity.CRITICAL)));

        verifyNoInteractions(subscriptions, jsonSupport);
    }

    @Test
    @DisplayName("a critical alert goes to every subscription with high urgency and a tap target")
    void criticalAlertIsPushed() {
        when(client.enabled()).thenReturn(true);
        when(jsonSupport.toJson(any())).thenAnswer(invocation -> invocation.getArgument(0).toString());
        PushSubscriptionService.Target phone = new PushSubscriptionService.Target(1L, "https://fcm.googleapis.com/x", "k", "a");
        when(subscriptions.recipients()).thenReturn(List.of(phone));
        when(client.send(eq(phone.endpoint()), any(), any(), any(), eq(WebPushClient.Urgency.HIGH)))
                .thenReturn(new WebPushClient.Delivery(WebPushClient.Outcome.DELIVERED, 201));

        service.onAlertRaised(new AlertRaisedEvent(alert(AlertSeverity.CRITICAL)));

        ArgumentCaptor<byte[]> payload = ArgumentCaptor.forClass(byte[].class);
        verify(client, timeout(2000)).send(eq(phone.endpoint()), eq("k"), eq("a"), payload.capture(), eq(WebPushClient.Urgency.HIGH));
        verify(subscriptions, timeout(2000)).recordDelivered(1L, NOW);
        assertThat(new String(payload.getValue(), StandardCharsets.UTF_8))
                .contains("title=Listrik PLN padam", "tag=alert-41", "url=/alerts", "severity=CRITICAL");
    }

    @Test
    @DisplayName("expired subscriptions are removed and temporary failures are only counted")
    void outcomesAreRecorded() {
        when(jsonSupport.toJson(any())).thenReturn("{}");
        PushSubscriptionService.Target ok = new PushSubscriptionService.Target(1L, "https://fcm.googleapis.com/1", "k", "a");
        PushSubscriptionService.Target gone = new PushSubscriptionService.Target(2L, "https://fcm.googleapis.com/2", "k", "a");
        PushSubscriptionService.Target flaky = new PushSubscriptionService.Target(3L, "https://fcm.googleapis.com/3", "k", "a");
        when(subscriptions.forUser("operator")).thenReturn(List.of(ok, gone, flaky));
        when(client.send(eq(ok.endpoint()), any(), any(), any(), any()))
                .thenReturn(new WebPushClient.Delivery(WebPushClient.Outcome.DELIVERED, 201));
        when(client.send(eq(gone.endpoint()), any(), any(), any(), any()))
                .thenReturn(new WebPushClient.Delivery(WebPushClient.Outcome.GONE, 410));
        when(client.send(eq(flaky.endpoint()), any(), any(), any(), any()))
                .thenReturn(new WebPushClient.Delivery(WebPushClient.Outcome.FAILED, 503));

        PushTestResponse result = service.sendTest("operator");

        assertThat(result).isEqualTo(new PushTestResponse(1, 1, 1));
        verify(subscriptions).recordDelivered(1L, NOW);
        verify(subscriptions).remove(2L);
        verify(subscriptions).recordFailed(3L);
        verify(subscriptions, never()).remove(3L);
    }
}
