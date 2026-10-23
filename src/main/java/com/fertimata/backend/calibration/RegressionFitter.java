package com.fertimata.backend.calibration;

import com.fertimata.backend.common.exception.BusinessRuleException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * Regresi kuadrat terkecil biasa untuk koreksi sensor terhadap lab (Protokol-Data.md §7 langkah 3-4).
 *
 * <p>Sengaja kecil: paling banyak beberapa prediktor (nilai mentah + kelembapan + suhu) dan puluhan
 * sampel, jadi persamaan normal yang diselesaikan dengan eliminasi Gauss sudah cukup. Tim tetap bebas
 * menghitung model di perangkat lunak statistik dan memasukkan koefisiennya langsung.
 */
public final class RegressionFitter {

    private static final double SINGULAR_EPSILON = 1e-12;

    private RegressionFitter() {}

    /** Satu sampel: nilai mentah sensor, nilai lab, dan nilai kovariat (metricKey -> nilai). */
    public record Point(double raw, double lab, Map<String, Double> covariates) {}

    /**
     * @param bias Bland-Altman: rata-rata (prediksi − lab)
     * @param loaLower batas bawah kesesuaian, bias − 1,96 SD
     * @param loaUpper batas atas kesesuaian, bias + 1,96 SD
     */
    public record Fit(
            CalibrationModel model,
            int sampleCount,
            double rSquared,
            double rmse,
            double mae,
            double bias,
            double loaLower,
            double loaUpper) {}

    public static Fit fit(List<Point> points, List<String> covariateKeys) {
        List<String> keys = covariateKeys == null ? List.of() : List.copyOf(covariateKeys);
        int parameters = 2 + keys.size();
        List<Point> usable = new ArrayList<>();
        for (Point p : points) {
            if (keys.stream().allMatch(k -> p.covariates() != null && p.covariates().get(k) != null)) {
                usable.add(p);
            }
        }
        if (usable.size() < parameters + 1) {
            throw new BusinessRuleException(
                    "At least %d samples with every covariate are needed to fit %d parameters; %d available"
                            .formatted(parameters + 1, parameters, usable.size()),
                    Map.of("required", parameters + 1, "available", usable.size()));
        }

        // Persamaan normal (XᵀX) β = Xᵀy dengan X = [1, raw, kovariat...].
        double[][] xtx = new double[parameters][parameters];
        double[] xty = new double[parameters];
        for (Point p : usable) {
            double[] row = row(p, keys);
            for (int i = 0; i < parameters; i++) {
                xty[i] += row[i] * p.lab();
                for (int j = 0; j < parameters; j++) {
                    xtx[i][j] += row[i] * row[j];
                }
            }
        }
        double[] beta = solve(xtx, xty);

        Map<String, Double> coefficients = new LinkedHashMap<>();
        for (int k = 0; k < keys.size(); k++) {
            coefficients.put(keys.get(k), beta[k + 2]);
        }
        CalibrationModel model = new CalibrationModel(beta[0], beta[1], coefficients);
        return evaluate(model, usable);
    }

    /** Statistik kesesuaian sebuah model terhadap sampel; dipakai juga untuk model yang dimasukkan manual. */
    public static Fit evaluate(CalibrationModel model, List<Point> points) {
        List<double[]> pairs = new ArrayList<>();
        for (Point p : points) {
            OptionalDouble predicted = model.apply(p.raw(), p.covariates());
            if (predicted.isPresent()) {
                pairs.add(new double[] {predicted.getAsDouble(), p.lab()});
            }
        }
        int n = pairs.size();
        if (n == 0) {
            return new Fit(model, 0, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        }
        double meanLab = pairs.stream().mapToDouble(pr -> pr[1]).average().orElse(0);
        double ssRes = 0;
        double ssTot = 0;
        double absSum = 0;
        double diffSum = 0;
        for (double[] pr : pairs) {
            double diff = pr[0] - pr[1];
            ssRes += diff * diff;
            ssTot += (pr[1] - meanLab) * (pr[1] - meanLab);
            absSum += Math.abs(diff);
            diffSum += diff;
        }
        double bias = diffSum / n;
        double sdSum = 0;
        for (double[] pr : pairs) {
            double d = (pr[0] - pr[1]) - bias;
            sdSum += d * d;
        }
        double sd = n > 1 ? Math.sqrt(sdSum / (n - 1)) : 0;
        double rSquared = ssTot == 0 ? Double.NaN : 1 - ssRes / ssTot;
        return new Fit(model, n, rSquared, Math.sqrt(ssRes / n), absSum / n, bias, bias - 1.96 * sd, bias + 1.96 * sd);
    }

    private static double[] row(Point p, List<String> keys) {
        double[] row = new double[2 + keys.size()];
        row[0] = 1;
        row[1] = p.raw();
        for (int k = 0; k < keys.size(); k++) {
            row[k + 2] = p.covariates().get(keys.get(k));
        }
        return row;
    }

    /** Eliminasi Gauss dengan pivot parsial. */
    static double[] solve(double[][] a, double[] b) {
        int n = b.length;
        double[][] m = new double[n][n + 1];
        double scale = 0;
        for (int i = 0; i < n; i++) {
            System.arraycopy(a[i], 0, m[i], 0, n);
            m[i][n] = b[i];
            scale = Math.max(scale, Math.abs(a[i][i]));
        }
        // Relatif terhadap besar matriks: EC dalam uS/cm membuat entri XᵀX berorde 10^7.
        double threshold = Math.max(scale, 1) * SINGULAR_EPSILON;
        for (int col = 0; col < n; col++) {
            int pivot = col;
            for (int r = col + 1; r < n; r++) {
                if (Math.abs(m[r][col]) > Math.abs(m[pivot][col])) {
                    pivot = r;
                }
            }
            if (Math.abs(m[pivot][col]) < threshold) {
                throw new BusinessRuleException(
                        "The samples cannot determine the model: a predictor is constant or duplicates another");
            }
            double[] tmp = m[col];
            m[col] = m[pivot];
            m[pivot] = tmp;
            for (int r = 0; r < n; r++) {
                if (r == col) {
                    continue;
                }
                double factor = m[r][col] / m[col][col];
                for (int c = col; c <= n; c++) {
                    m[r][c] -= factor * m[col][c];
                }
            }
        }
        double[] x = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = m[i][n] / m[i][i];
        }
        return x;
    }
}
