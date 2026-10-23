package com.fertimata.backend.calibration;

/** Bentuk model koreksi sensor. Keduanya linear; bedanya hanya ada tidaknya kovariat. */
public enum CalibrationMethod {
    /** {@code intercept + slope * raw}. */
    LINEAR,
    /** {@code intercept + slope * raw + Σ coef_i * kovariat_i} (Protokol-Data.md §7 langkah 4). */
    REGRESSION
}
