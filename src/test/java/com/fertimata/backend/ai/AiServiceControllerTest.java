package com.fertimata.backend.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AiServiceControllerTest {

    /** Jam yang bisa dimajukan, untuk menguji masa simpan status. */
    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-11-02T16:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final AiServiceClient client = mock(AiServiceClient.class);
    private final MovableClock clock = new MovableClock();
    private final AiServiceController controller = new AiServiceController(client, clock);

    @Test
    @DisplayName("status: the AI health answer is reused for 15 s, then checked again")
    void statusIsCached() throws Exception {
        when(client.configured()).thenReturn(true);
        when(client.health()).thenReturn("Running Melon AI System API");

        AiServiceController.Status first = controller.status();
        clock.advance(Duration.ofSeconds(10));
        AiServiceController.Status second = controller.status();

        assertThat(first.reachable()).isTrue();
        assertThat(second).isSameAs(first);
        verify(client, times(1)).health();

        clock.advance(Duration.ofSeconds(6));
        when(client.health()).thenThrow(new AiServiceException("AI service unreachable: refused"));
        AiServiceController.Status third = controller.status();

        assertThat(third.reachable()).isFalse();
        assertThat(third.message()).contains("unreachable");
        verify(client, times(2)).health();
    }

    @Test
    @DisplayName("status: AI_SERVICE_URL empty = configured false, the AI is never called")
    void notConfigured() throws Exception {
        when(client.configured()).thenReturn(false);

        AiServiceController.Status status = controller.status();

        assertThat(status.configured()).isFalse();
        assertThat(status.reachable()).isFalse();
        verify(client, times(0)).health();
    }
}
