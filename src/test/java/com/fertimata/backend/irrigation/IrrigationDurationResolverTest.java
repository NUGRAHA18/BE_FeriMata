package com.fertimata.backend.irrigation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fertimata.backend.ai.AiInputsResponse;
import com.fertimata.backend.ai.AiInputsService;
import com.fertimata.backend.ai.AiServiceClient;
import com.fertimata.backend.ai.AiServiceException;
import com.fertimata.backend.irrigation.IrrigationDurationResolver.Decision;
import com.fertimata.backend.irrigation.IrrigationDurationResolver.Source;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IrrigationDurationResolverTest {

    private static final Map<String, BigDecimal> FORM = Map.of("humidity_tanah", new BigDecimal("35"), "hst", BigDecimal.TEN);

    @Mock
    private AiInputsService aiInputs;

    @Mock
    private AiServiceClient aiClient;

    private IrrigationDurationResolver resolver(Source source) {
        return new IrrigationDurationResolver(
                new IrrigationProperties(true, "DIST-PUMP", Duration.ofSeconds(30), Duration.ofMinutes(15), source),
                aiInputs,
                aiClient);
    }

    private static AiInputsResponse inputs(boolean ready, List<String> problems) {
        return new AiInputsResponse(
                Instant.EPOCH, 900, 10, "SEMAI", "Semai", ready, ready, Map.of(), FORM, Map.of(), problems);
    }

    @Test
    @DisplayName("the AI fuzzy irrigation decides the duration, rounded to whole seconds")
    void durationFromAi() throws Exception {
        when(aiClient.configured()).thenReturn(true);
        when(aiInputs.inputs()).thenReturn(inputs(true, List.of()));
        when(aiClient.irrigationSeconds(FORM)).thenReturn(84.6);

        assertThat(resolver(null).resolve(300, 600)).isEqualTo(new Decision(85, Source.AI, null, null));
    }

    @Test
    @DisplayName("an AI duration above the pump limit is capped so the safety check does not refuse it")
    void cappedToPumpLimit() throws Exception {
        when(aiClient.configured()).thenReturn(true);
        when(aiInputs.inputs()).thenReturn(inputs(true, List.of()));
        when(aiClient.irrigationSeconds(FORM)).thenReturn(150.0);

        Decision decision = resolver(Source.AI).resolve(300, 120);

        assertThat(decision.seconds()).isEqualTo(120);
        assertThat(decision.source()).isEqualTo(Source.AI);
        assertThat(decision.reasonCode()).isEqualTo(IrrigationDurationResolver.AI_CAPPED);
    }

    @Test
    @DisplayName("an AI answer of 0 s means no watering")
    void zeroMeansSkip() throws Exception {
        when(aiClient.configured()).thenReturn(true);
        when(aiInputs.inputs()).thenReturn(inputs(true, List.of()));
        when(aiClient.irrigationSeconds(FORM)).thenReturn(0.2);

        Decision decision = resolver(Source.AI).resolve(300, null);

        assertThat(decision.skip()).isTrue();
        assertThat(decision.reasonCode()).isEqualTo(IrrigationDurationResolver.AI_NO_WATER_NEEDED);
    }

    @Test
    @DisplayName("stale or missing sensor data falls back to the schedule's duration without calling the AI")
    void staleInputsFallBack() throws Exception {
        when(aiClient.configured()).thenReturn(true);
        when(aiInputs.inputs()).thenReturn(inputs(false, List.of("irrigation.humidity_tanah (soil_a.moisture): no reading")));

        Decision decision = resolver(Source.AI).resolve(300, null);

        assertThat(decision).extracting(Decision::seconds, Decision::source, Decision::reasonCode)
                .containsExactly(300, Source.FALLBACK, IrrigationDurationResolver.AI_INPUTS_NOT_READY);
        assertThat(decision.detail()).contains("soil_a.moisture");
        verify(aiClient, never()).irrigationSeconds(any());
    }

    @Test
    @DisplayName("an unreachable AI service falls back to the schedule's duration")
    void unreachableFallsBack() throws Exception {
        when(aiClient.configured()).thenReturn(true);
        when(aiInputs.inputs()).thenReturn(inputs(true, List.of()));
        when(aiClient.irrigationSeconds(FORM)).thenThrow(new AiServiceException("AI service unreachable: refused"));

        assertThat(resolver(Source.AI).resolve(300, null))
                .extracting(Decision::seconds, Decision::source, Decision::reasonCode)
                .containsExactly(300, Source.FALLBACK, IrrigationDurationResolver.AI_UNAVAILABLE);
    }

    @Test
    @DisplayName("without AI_SERVICE_URL the schedule's duration is used")
    void notConfigured() {
        when(aiClient.configured()).thenReturn(false);

        assertThat(resolver(Source.AI).resolve(300, null).reasonCode()).isEqualTo(IrrigationDurationResolver.AI_NOT_CONFIGURED);
        verify(aiInputs, never()).inputs();
    }

    @Test
    @DisplayName("MANUAL always uses the farmer's duration")
    void manual() {
        assertThat(resolver(Source.MANUAL).resolve(300, null)).isEqualTo(new Decision(300, Source.MANUAL, null, null));
    }
}
