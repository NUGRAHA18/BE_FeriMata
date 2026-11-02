package com.fertimata.backend.alert;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NewAlertTest {

    @Test
    void longTitleAndMessageAreCutToTheColumnSizes() {
        // Seperti daftar 16 data sensor basi di peringatan durasi cadangan (±1.200 karakter).
        NewAlert alert = NewAlert.builder(AlertType.IRRIGATION_AI_FALLBACK, AlertSeverity.INFO)
                .title("t".repeat(300))
                .message("m".repeat(1200))
                .build();

        assertThat(alert.title()).hasSize(NewAlert.TITLE_LENGTH).endsWith("…");
        assertThat(alert.message()).hasSize(NewAlert.MESSAGE_LENGTH).endsWith("…");
    }

    @Test
    void shortTextIsKeptAsIs() {
        NewAlert alert = NewAlert.builder(AlertType.IRRIGATION_AI_FALLBACK, AlertSeverity.INFO)
                .title("Irrigation used the fallback duration")
                .message("x".repeat(NewAlert.MESSAGE_LENGTH))
                .build();

        assertThat(alert.title()).isEqualTo("Irrigation used the fallback duration");
        assertThat(alert.message()).hasSize(NewAlert.MESSAGE_LENGTH).doesNotContain("…");
    }
}
