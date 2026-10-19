package com.fertimata.backend.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fertimata.backend.common.util.JsonSupport;
import com.fertimata.backend.device.Device;
import com.fertimata.backend.device.DeviceService;
import com.fertimata.backend.mqtt.payload.AiDetectionPayload;
import com.fertimata.backend.plant.PlantRepository;
import com.fertimata.backend.support.TestFixtures;
import com.fertimata.backend.websocket.RealtimeEventPublisher;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiDetectionServiceTest {

    @Mock
    private AiDetectionRepository detectionRepository;

    @Mock
    private PlantRepository plantRepository;

    @Mock
    private DeviceService deviceService;

    @Mock
    private AiDetectionMapper mapper;

    @Mock
    private RealtimeEventPublisher realtimeEventPublisher;

    @Mock
    private JsonSupport jsonSupport;

    private AiDetectionService service;
    private Device jetson;

    @BeforeEach
    void setUp() {
        service = new AiDetectionService(
                detectionRepository, plantRepository, deviceService, mapper, realtimeEventPublisher, jsonSupport);
        jetson = TestFixtures.onlineDevice(1L, "JETSON-01");
        when(deviceService.resolveForIngest("JETSON-01")).thenReturn(Optional.of(jetson));
        when(detectionRepository.save(any(AiDetection.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jsonSupport.toJson(any())).thenAnswer(invocation -> String.valueOf((Object) invocation.getArgument(0)));
        when(mapper.toResponse(any())).thenReturn(org.mockito.Mockito.mock(AiDetectionResponse.class));
    }

    @Test
    @DisplayName("a scores-only multi-label result is stored with its station and capture, without a label")
    void storesMultiLabelScores() {
        Map<String, BigDecimal> scores = Map.of(
                "Leaf_N_stress", new BigDecimal("0.71"),
                "Leaf_K_stress", new BigDecimal("0.64"));

        Optional<AiDetectionResponse> result = service.record("JETSON-01", new AiDetectionPayload(
                null, null, "NUTRIENT_DEFICIENCY", null, null, null, null, scores, "ST-03", "cap-42", null));

        assertThat(result).isNotNull();
        ArgumentCaptor<AiDetection> captor = ArgumentCaptor.forClass(AiDetection.class);
        verify(detectionRepository).save(captor.capture());
        AiDetection saved = captor.getValue();
        assertThat(saved.getLabel()).isNull();
        assertThat(saved.getScores()).contains("Leaf_N_stress");
        assertThat(saved.getStationCode()).isEqualTo("ST-03");
        assertThat(saved.getCaptureId()).isEqualTo("cap-42");
    }

    @Test
    @DisplayName("a result with neither a label nor scores is ignored")
    void rejectsEmptyResult() {
        Optional<AiDetectionResponse> result = service.record("JETSON-01", new AiDetectionPayload(
                null, null, "NUTRIENT_DEFICIENCY", " ", null, null, null, Map.of(), "ST-03", null, null));

        assertThat(result).isEmpty();
        verify(detectionRepository, never()).save(any());
    }
}
