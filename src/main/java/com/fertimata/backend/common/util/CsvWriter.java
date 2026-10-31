package com.fertimata.backend.common.util;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** CSV RFC 4180 minimal; diawali BOM UTF-8 agar Excel membaca "°"/"µ", angka tanpa nol di belakang. */
public final class CsvWriter {

    private final StringBuilder out = new StringBuilder("\uFEFF");

    public CsvWriter row(List<?> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(escape(values.get(i)));
        }
        out.append("\r\n");
        return this;
    }

    public byte[] toBytes() {
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    static String escape(Object value) {
        if (value == null) {
            return "";
        }
        // Kolom NUMERIC(18,6) datang sebagai 120.000000; tulis 120 supaya spreadsheet tetap rapi.
        String text = value instanceof java.math.BigDecimal d ? d.stripTrailingZeros().toPlainString() : value.toString();
        // Sel yang diawali = + - @ dieksekusi sebagai rumus oleh Excel; teks bebas (catatan) diberi awalan '.
        if (!(value instanceof Number) && !text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
