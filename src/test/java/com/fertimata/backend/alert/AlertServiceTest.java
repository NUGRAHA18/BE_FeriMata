package com.fertimata.backend.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fertimata.backend.common.exception.BusinessRuleException;
import com.fertimata.backend.common.exception.ResourceNotFoundException;
import com.fertimata.backend.common.util.JsonSupport;
import com.fertimata.backend.device.Device;
import com.fertimata.backend.support.TestFixtures;
import com.fertimata.backend.user.User;
import com.fertimata.backend.user.UserRepository;
import com.fertimata.backend.websocket.RealtimeEventPublisher;
import com.fertimata.backend.websocket.RealtimeEventType;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RealtimeEventPublisher realtimeEventPublisher;

    @Mock
    private JsonSupport jsonSupport;

    @InjectMocks
    private AlertService alertService;

    @Test
    @DisplayName("raising an alert persists it and pushes it to connected clients")
    void raiseAlert() {
        Device device = TestFixtures.device(1L, "JETSON-001");
        when(alertRepository.save(any(Alert.class))).thenAnswer(invocation -> {
            Alert alert = invocation.getArgument(0);
            TestFixtures.setId(alert, 9L);
            return alert;
        });

        AlertResponse response = alertService.raise(NewAlert.builder(AlertType.DEVICE_OFFLINE, AlertSeverity.CRITICAL)
                .title("Device offline")
                .message("No message received")
                .source(AlertSource.SYSTEM)
                .device(device)
                .build());

        assertThat(response.id()).isEqualTo(9L);
        assertThat(response.type()).isEqualTo(AlertType.DEVICE_OFFLINE);
        assertThat(response.severity()).isEqualTo(AlertSeverity.CRITICAL);
        assertThat(response.relatedDeviceCode()).isEqualTo("JETSON-001");
        assertThat(response.acknowledged()).isFalse();
        verify(realtimeEventPublisher).publish(eq(RealtimeEventType.ALERT_CREATED), any());
    }

    @Test
    @DisplayName("a repeat infrastructure alert is suppressed while the first one is still open")
    void suppressesDuplicateDeviceAlert() {
        Device device = TestFixtures.device(1L, "JETSON-001");
        when(alertRepository.existsByTypeAndRelatedDeviceIdAndAcknowledgedFalse(AlertType.DEVICE_OFFLINE, 1L))
                .thenReturn(true);

        alertService.raiseOnceForDevice(NewAlert.builder(AlertType.DEVICE_OFFLINE, AlertSeverity.CRITICAL)
                .title("Device offline")
                .message("No message received")
                .device(device)
                .build());

        verify(alertRepository, never()).save(any());
        verify(realtimeEventPublisher, never()).publish(any(), any());
    }

    @Test
    @DisplayName("acknowledging records who did it and when")
    void acknowledgeAlert() {
        Alert alert = new Alert();
        TestFixtures.setId(alert, 9L);
        alert.setType(AlertType.DEVICE_OFFLINE);
        alert.setSeverity(AlertSeverity.CRITICAL);
        alert.setTitle("Device offline");
        alert.setMessage("No message received");
        alert.setSource(AlertSource.SYSTEM);

        User operator = TestFixtures.operator(3L, "operator");
        when(alertRepository.findById(9L)).thenReturn(Optional.of(alert));
        when(userRepository.findByUsername("operator")).thenReturn(Optional.of(operator));

        AlertResponse response = alertService.acknowledge(9L, "operator");

        assertThat(response.acknowledged()).isTrue();
        assertThat(response.acknowledgedBy()).isEqualTo("operator");
        assertThat(response.acknowledgedAt()).isNotNull();
    }

    @Test
    @DisplayName("acknowledging twice is refused so the first acknowledger is not overwritten")
    void doubleAcknowledgeIsRefused() {
        Alert alert = new Alert();
        TestFixtures.setId(alert, 9L);
        alert.acknowledge(TestFixtures.operator(3L, "first"), java.time.Instant.now());
        when(alertRepository.findById(9L)).thenReturn(Optional.of(alert));

        assertThatThrownBy(() -> alertService.acknowledge(9L, "second"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already been acknowledged");
    }

    @Test
    @DisplayName("acknowledging an alert that does not exist is a 404")
    void acknowledgeMissingAlert() {
        when(alertRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> alertService.acknowledge(404L, "operator"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
