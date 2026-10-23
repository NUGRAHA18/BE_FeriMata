package com.fertimata.backend.calibration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.fertimata.backend.common.exception.BusinessRuleException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RegressionFitterTest {

    @Test
    @DisplayName("a perfectly linear sample set is recovered exactly")
    void recoversLinearModel() {
        List<RegressionFitter.Point> points = new ArrayList<>();
        for (int raw = 100; raw <= 500; raw += 50) {
            points.add(new RegressionFitter.Point(raw, 2 + 0.5 * raw, Map.of()));
        }

        RegressionFitter.Fit fit = RegressionFitter.fit(points, List.of());

        assertThat(fit.model().intercept()).isCloseTo(2, within(1e-9));
        assertThat(fit.model().slope()).isCloseTo(0.5, within(1e-9));
        assertThat(fit.rSquared()).isCloseTo(1, within(1e-12));
        assertThat(fit.rmse()).isCloseTo(0, within(1e-9));
        assertThat(fit.sampleCount()).isEqualTo(points.size());
    }

    @Test
    @DisplayName("a moisture covariate gets its own coefficient")
    void recoversCovariate() {
        List<RegressionFitter.Point> points = new ArrayList<>();
        double[][] data = {{120, 30}, {180, 45}, {240, 35}, {300, 50}, {360, 28}, {420, 41}, {150, 52}};
        for (double[] d : data) {
            double lab = 1.0 + 0.2 * d[0] - 0.3 * d[1];
            points.add(new RegressionFitter.Point(d[0], lab, Map.of("soil_a.moisture", d[1])));
        }

        RegressionFitter.Fit fit = RegressionFitter.fit(points, List.of("soil_a.moisture"));

        assertThat(fit.model().intercept()).isCloseTo(1.0, within(1e-6));
        assertThat(fit.model().slope()).isCloseTo(0.2, within(1e-9));
        assertThat(fit.model().covariates().get("soil_a.moisture")).isCloseTo(-0.3, within(1e-9));
        assertThat(fit.rSquared()).isCloseTo(1, within(1e-9));
    }

    @Test
    @DisplayName("samples without every covariate are skipped and too few samples are refused")
    void refusesTooFewSamples() {
        List<RegressionFitter.Point> points = List.of(
                new RegressionFitter.Point(1, 2, Map.of("t", 1.0)),
                new RegressionFitter.Point(2, 3, Map.of()),
                new RegressionFitter.Point(3, 5, Map.of("t", 2.0)));

        assertThatThrownBy(() -> RegressionFitter.fit(points, List.of("t")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("At least 4 samples");
    }

    @Test
    @DisplayName("a constant raw value cannot determine a slope")
    void refusesSingularSystem() {
        List<RegressionFitter.Point> points = List.of(
                new RegressionFitter.Point(1413, 1.0, Map.of()),
                new RegressionFitter.Point(1413, 1.1, Map.of()),
                new RegressionFitter.Point(1413, 0.9, Map.of()));

        assertThatThrownBy(() -> RegressionFitter.fit(points, List.of()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot determine");
    }

    @Test
    @DisplayName("Bland-Altman bias and limits of agreement describe the residuals")
    void blandAltman() {
        CalibrationModel model = new CalibrationModel(0, 1, Map.of());
        List<RegressionFitter.Point> points = List.of(
                new RegressionFitter.Point(11, 10, Map.of()),
                new RegressionFitter.Point(21, 20, Map.of()),
                new RegressionFitter.Point(33, 30, Map.of()));

        RegressionFitter.Fit stats = RegressionFitter.evaluate(model, points);

        // Selisih 1, 1, 3: rata-rata 5/3, SD sampel 1,1547.
        assertThat(stats.bias()).isCloseTo(5.0 / 3, within(1e-9));
        assertThat(stats.loaUpper() - stats.loaLower()).isCloseTo(2 * 1.96 * 1.1547005, within(1e-6));
        assertThat(stats.mae()).isCloseTo(5.0 / 3, within(1e-9));
    }

    @Test
    @DisplayName("a model with a missing covariate yields no value instead of treating it as zero")
    void missingCovariate() {
        CalibrationModel model = new CalibrationModel(1, 2, Map.of("soil_a.moisture", 0.5));

        assertThat(model.apply(10, Map.of())).isEmpty();
        assertThat(model.apply(10, Map.of("soil_a.moisture", 4.0))).hasValue(23.0);
    }
}
