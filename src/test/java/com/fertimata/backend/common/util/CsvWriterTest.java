package com.fertimata.backend.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CsvWriterTest {

    @Test
    @DisplayName("quotes commas and quotes, neutralises formulas, keeps numbers and treatment codes as they are")
    void escaping() {
        byte[] bytes = new CsvWriter()
                .row(List.of("plant", "notes"))
                .row(Arrays.asList("B1-N-02", "daun \"kuning\", tepi kering", new BigDecimal("1E+3"), -2, null, "=HYPERLINK()", "N-"))
                .toBytes();
        String text = new String(bytes, StandardCharsets.UTF_8);

        assertThat(text).startsWith("\uFEFFplant,notes\r\n");
        assertThat(text).contains("B1-N-02,\"daun \"\"kuning\"\", tepi kering\",1000,-2,,'=HYPERLINK(),N-\r\n");
    }
}
