package com.fertimata.backend.calibration;

import java.util.Map;
import java.util.OptionalDouble;

/**
 * Bentuk siap-hitung dari {@link SensorCalibration}: {@code intercept + slope * raw + Σ coef_i * kovariat_i}.
 *
 * @param covariates metricKey kovariat -> koefisien; kosong untuk model linear biasa
 */
public record CalibrationModel(double intercept, double slope, Map<String, Double> covariates) {

    public CalibrationModel {
        covariates = covariates == null ? Map.of() : Map.copyOf(covariates);
    }

    public boolean needsCovariates() {
        return !covariates.isEmpty();
    }

    /**
     * Nilai terkoreksi, atau kosong bila ada kovariat yang tidak tersedia. Lebih baik tidak menampilkan
     * angka daripada menampilkan angka yang dihitung dengan kovariat dianggap nol.
     */
    public OptionalDouble apply(double raw, Map<String, Double> covariateValues) {
        double result = intercept + slope * raw;
        for (Map.Entry<String, Double> term : covariates.entrySet()) {
            Double value = covariateValues == null ? null : covariateValues.get(term.getKey());
            if (value == null) {
                return OptionalDouble.empty();
            }
            result += term.getValue() * value;
        }
        return OptionalDouble.of(result);
    }
}
