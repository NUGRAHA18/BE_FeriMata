package com.fertimata.backend.irrigation;

import com.fertimata.backend.ai.AiInputsResponse;
import com.fertimata.backend.ai.AiInputsService;
import com.fertimata.backend.ai.AiServiceClient;
import com.fertimata.backend.ai.AiServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Lama satu jadwal siram (keputusan 31 Okt 2026): dari fuzzy irigasi AI ({@code run_irrigation}); durasi isian petani
 * jadi cadangan bila AI tidak bisa dipakai. AI 0 detik = tidak perlu siram.
 */
@Component
public class IrrigationDurationResolver {

    private static final Logger log = LoggerFactory.getLogger(IrrigationDurationResolver.class);

    static final String AI_NOT_CONFIGURED = "AI_NOT_CONFIGURED";
    static final String AI_INPUTS_NOT_READY = "AI_INPUTS_NOT_READY";
    static final String AI_UNAVAILABLE = "AI_UNAVAILABLE";
    static final String AI_NO_WATER_NEEDED = "AI_NO_WATER_NEEDED";
    static final String AI_CAPPED = "AI_CAPPED";

    public enum Source {
        /** Dari fuzzy irigasi AI. */
        AI,
        /** AI diminta tetapi tidak bisa dipakai; memakai durasi cadangan jadwal. */
        FALLBACK,
        /** {@code app.irrigation.duration-source=MANUAL}: selalu durasi isian petani. */
        MANUAL
    }

    /** {@code seconds} 0 = tidak siram; {@code reasonCode} null bila durasi AI dipakai apa adanya. */
    public record Decision(int seconds, Source source, String reasonCode, String detail) {
        public boolean skip() {
            return seconds <= 0;
        }
    }

    private final IrrigationProperties properties;
    private final AiInputsService aiInputs;
    private final AiServiceClient aiClient;

    public IrrigationDurationResolver(
            IrrigationProperties properties, AiInputsService aiInputs, AiServiceClient aiClient) {
        this.properties = properties;
        this.aiInputs = aiInputs;
        this.aiClient = aiClient;
    }

    /** {@code maxRunSeconds}: batas pompa; durasi AI dipotong ke sini agar tidak ditolak. */
    public Decision resolve(int fallbackSeconds, Integer maxRunSeconds) {
        if (properties.durationSource() == Source.MANUAL) {
            return new Decision(fallbackSeconds, Source.MANUAL, null, null);
        }
        if (!aiClient.configured()) {
            return fallback(fallbackSeconds, AI_NOT_CONFIGURED, "AI_SERVICE_URL is empty");
        }
        AiInputsResponse inputs = aiInputs.inputs();
        if (!inputs.readyForIrrigation()) {
            return fallback(fallbackSeconds, AI_INPUTS_NOT_READY, String.join("; ", inputs.problems()));
        }
        double aiSeconds;
        try {
            aiSeconds = aiClient.irrigationSeconds(inputs.irrigation());
        } catch (AiServiceException ex) {
            return fallback(fallbackSeconds, AI_UNAVAILABLE, ex.getMessage());
        }
        int seconds = (int) Math.round(aiSeconds);
        if (seconds <= 0) {
            return new Decision(0, Source.AI, AI_NO_WATER_NEEDED, "AI fuzzy irrigation returned %s s".formatted(aiSeconds));
        }
        if (maxRunSeconds != null && seconds > maxRunSeconds) {
            return new Decision(
                    maxRunSeconds,
                    Source.AI,
                    AI_CAPPED,
                    "AI asked for %d s; capped to the pump limit of %d s".formatted(seconds, maxRunSeconds));
        }
        return new Decision(seconds, Source.AI, null, null);
    }

    private Decision fallback(int fallbackSeconds, String reasonCode, String detail) {
        log.warn("Irrigation duration from AI unavailable ({}): {}; using the schedule's {} s", reasonCode, detail, fallbackSeconds);
        return new Decision(fallbackSeconds, Source.FALLBACK, reasonCode, detail);
    }
}
