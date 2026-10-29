package com.fertimata.backend.actuator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fertimata.backend.actuator.domain.Actuator;
import com.fertimata.backend.actuator.domain.ActuatorRepository;
import com.fertimata.backend.actuator.dto.ActuatorResponse;
import com.fertimata.backend.actuator.dto.ActuatorUpdateRequest;
import com.fertimata.backend.actuator.safety.CommandVocabularyProperties;
import com.fertimata.backend.common.util.JsonSupport;
import com.fertimata.backend.device.DeviceService;
import com.fertimata.backend.support.TestFixtures;
import com.fertimata.backend.websocket.RealtimeEventPublisher;
import com.fertimata.backend.websocket.RealtimeEventType;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActuatorServiceTest {

    @Mock
    private ActuatorRepository actuatorRepository;

    @Mock
    private DeviceService deviceService;

    @Mock
    private RealtimeEventPublisher realtimeEventPublisher;

    @Mock
    private JsonSupport jsonSupport;

    @Test
    @DisplayName("changing an actuator's settings is pushed to other dashboards")
    void updatePublishesActuatorUpdated() {
        Actuator actuator = TestFixtures.actuator(7L, TestFixtures.device(1L, "RIO-TANK-01"), "DOSING-N");
        when(actuatorRepository.findByIdWithDevice(7L)).thenReturn(Optional.of(actuator));
        ActuatorService service = new ActuatorService(
                actuatorRepository, deviceService, realtimeEventPublisher, jsonSupport, CommandVocabularyProperties.open());

        ActuatorResponse response =
                service.update(7L, new ActuatorUpdateRequest(null, null, null, false, 45, null));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(realtimeEventPublisher).publish(eq(RealtimeEventType.ACTUATOR_UPDATED), payload.capture());
        assertThat(payload.getValue()).isEqualTo(response);
        assertThat(response.enabled()).isFalse();
        assertThat(response.maxRunSeconds()).isEqualTo(45);
    }
}
