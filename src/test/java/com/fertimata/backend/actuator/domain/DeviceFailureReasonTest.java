package com.fertimata.backend.actuator.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DeviceFailureReasonTest {

    @Test
    @DisplayName("splits the edge's CODE: text format")
    void parsesCodedMessage() {
        DeviceFailureReason reason = DeviceFailureReason.parse("INTERLOCK_FLOAT: air di drum hampir habis");

        assertThat(reason.code()).isEqualTo("INTERLOCK_FLOAT");
        assertThat(reason.text()).isEqualTo("air di drum hampir habis");
    }

    @Test
    @DisplayName("a colon inside the farmer text does not confuse the split")
    void keepsLaterColons() {
        DeviceFailureReason reason = DeviceFailureReason.parse("ERROR: gangguan: coba lagi");

        assertThat(reason.code()).isEqualTo("ERROR");
        assertThat(reason.text()).isEqualTo("gangguan: coba lagi");
    }

    @ParameterizedTest
    @ValueSource(strings = {"relay stuck", "Relay: stuck", "INTERLOCK_POWER:", "interlock_power: padam", "A B: c"})
    @DisplayName("messages without a well-formed code are kept whole, with no code")
    void uncodedMessageIsKeptWhole(String message) {
        DeviceFailureReason reason = DeviceFailureReason.parse(message);

        assertThat(reason.code()).isNull();
        assertThat(reason.text()).isEqualTo(message.strip());
    }

    @Test
    @DisplayName("a missing message still yields readable text")
    void missingMessage() {
        assertThat(DeviceFailureReason.parse(null).text()).isNotBlank();
        assertThat(DeviceFailureReason.parse("  ").code()).isNull();
    }

    @Test
    @DisplayName("a code longer than the column is not treated as a code")
    void overlongCodeIsNotACode() {
        String message = "A".repeat(DeviceFailureReason.MAX_CODE_LENGTH + 1) + ": teks";

        assertThat(DeviceFailureReason.parse(message).code()).isNull();
    }
}
