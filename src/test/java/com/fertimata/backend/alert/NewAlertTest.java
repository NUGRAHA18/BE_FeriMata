package com.fertimata.backend.alert;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NewAlertTest {

    @Test
    void longTitleAndMessageAreCutToTheColumnSizes() {
        // Seperti daftar 16 data sensor basi di peringatan pemupukan (±1.200 karakter).
        NewAlert alert = NewAlert.builder(AlertType.FERTILIZATION_SKIPPED, AlertSeverity.INFO)
                .title("t".repeat(300))
                .message("m".repeat(1200))
                .build();

        assertThat(alert.title()).hasSize(NewAlert.TITLE_LENGTH).endsWith("…");
        assertThat(alert.message()).hasSize(NewAlert.MESSAGE_LENGTH).endsWith("…");
    }

    @Test
    void shortTextIsKeptAsIs() {
        NewAlert alert = NewAlert.builder(AlertType.FERTILIZATION_SKIPPED, AlertSeverity.INFO)
                .title("Fertilization did not run")
                .message("x".repeat(NewAlert.MESSAGE_LENGTH))
                .build();

        assertThat(alert.title()).isEqualTo("Fertilization did not run");
        assertThat(alert.message()).hasSize(NewAlert.MESSAGE_LENGTH).doesNotContain("…");
    }
}
