package com.fertimata.backend.fertilization;

import com.fertimata.backend.actuator.domain.Actuator;
import com.fertimata.backend.actuator.domain.ActuatorRepository;
import com.fertimata.backend.calibration.ActuatorCalibration;
import com.fertimata.backend.calibration.PumpCalibrationService;
import com.fertimata.backend.common.exception.BusinessRuleException;
import com.fertimata.backend.fertilization.dto.FertilizationDoseRequest;
import com.fertimata.backend.fertilization.dto.FertilizationDoseResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Takaran pupuk hybrid (keputusan 4 Nov 2026): bawaan dari fuzzy AI; bila petani mengisi takaran sendiri (ml), backend
 * yang menentukan lama pompa = ml / debit kalibrasi terbaru. Jarak hari tetap dari jadwal dosing AI.
 */
@Service
public class FertilizationDoseService {

    private static final Logger log = LoggerFactory.getLogger(FertilizationDoseService.class);

    /** N, P, K dalam urutan dosing. */
    enum Nutrient {
        N,
        P,
        K
    }

    /** Takaran manual yang sudah diubah ke detik (sebelum dibatasi {@code maxRunSeconds}). */
    public record ManualDose(
            BigDecimal nitrogenMl,
            BigDecimal phosphorusMl,
            BigDecimal potassiumMl,
            double nitrogenSeconds,
            double phosphorusSeconds,
            double potassiumSeconds) {}

    /** Takaran manual tidak bisa dipakai saat jalan (mis. kalibrasi pompa dihapus setelah disimpan). */
    public static class DoseUnavailableException extends Exception {
        private final String reasonCode;

        DoseUnavailableException(String reasonCode, String message) {
            super(message);
            this.reasonCode = reasonCode;
        }

        public String reasonCode() {
            return reasonCode;
        }
    }

    static final String PUMP_NOT_CALIBRATED = "PUMP_NOT_CALIBRATED";

    private final FertilizationDoseSettingRepository repository;
    private final ActuatorRepository actuatorRepository;
    private final PumpCalibrationService calibrations;
    private final FertilizationProperties properties;

    public FertilizationDoseService(
            FertilizationDoseSettingRepository repository,
            ActuatorRepository actuatorRepository,
            PumpCalibrationService calibrations,
            FertilizationProperties properties) {
        this.repository = repository;
        this.actuatorRepository = actuatorRepository;
        this.calibrations = calibrations;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public FertilizationDoseSetting.Mode mode() {
        return repository.findFirstByOrderByIdAsc().map(FertilizationDoseSetting::getMode).orElse(FertilizationDoseSetting.Mode.AI);
    }

    @Transactional(readOnly = true)
    public FertilizationDoseResponse dose() {
        return toResponse(repository.findFirstByOrderByIdAsc().orElse(null));
    }

    @Transactional
    public FertilizationDoseResponse update(FertilizationDoseRequest request, String username) {
        if (request.mode() == FertilizationDoseSetting.Mode.MANUAL) {
            validateManual(request);
        }
        FertilizationDoseSetting setting = repository.findFirstByOrderByIdAsc().orElseGet(FertilizationDoseSetting::new);
        setting.setMode(request.mode());
        // Mode AI tanpa angka: angka manual sebelumnya tetap disimpan.
        if (request.mode() == FertilizationDoseSetting.Mode.MANUAL
                || request.nitrogenMl() != null || request.phosphorusMl() != null || request.potassiumMl() != null) {
            setting.setNitrogenMl(request.nitrogenMl());
            setting.setPhosphorusMl(request.phosphorusMl());
            setting.setPotassiumMl(request.potassiumMl());
        }
        setting.setUpdatedBy(username);
        setting = repository.save(setting);
        log.info("Fertilization dose set to {} by {} (N {} ml, P {} ml, K {} ml)", setting.getMode(), username,
                setting.getNitrogenMl(), setting.getPhosphorusMl(), setting.getPotassiumMl());
        return toResponse(setting);
    }

    /** Detik pompa untuk takaran manual tersimpan, memakai kalibrasi terbaru saat ini. */
    @Transactional(readOnly = true)
    public ManualDose manualDose(Actuator nitrogen, Actuator phosphorus, Actuator potassium) throws DoseUnavailableException {
        FertilizationDoseSetting setting = repository.findFirstByOrderByIdAsc()
                .filter(s -> s.getMode() == FertilizationDoseSetting.Mode.MANUAL)
                .orElseThrow(() -> new IllegalStateException("Fertilization dose is not MANUAL"));
        BigDecimal n = zeroIfNull(setting.getNitrogenMl());
        BigDecimal p = zeroIfNull(setting.getPhosphorusMl());
        BigDecimal k = zeroIfNull(setting.getPotassiumMl());
        return new ManualDose(n, p, k, seconds(Nutrient.N, n, nitrogen), seconds(Nutrient.P, p, phosphorus),
                seconds(Nutrient.K, k, potassium));
    }

    private double seconds(Nutrient nutrient, BigDecimal ml, Actuator pump) throws DoseUnavailableException {
        if (ml.signum() <= 0) {
            return 0;
        }
        BigDecimal flow = flow(pump).orElseThrow(() -> new DoseUnavailableException(PUMP_NOT_CALIBRATED,
                "Pump %s (%s) has no flow calibration; a manual dose in ml needs one".formatted(pump.getCode(), nutrient)));
        // Takaran > 0 tetap menyalakan pompa paling tidak 1 detik.
        return Math.max(1.0, ml.doubleValue() / flow.doubleValue());
    }

    private void validateManual(FertilizationDoseRequest request) {
        Map<Nutrient, BigDecimal> ml = new LinkedHashMap<>();
        ml.put(Nutrient.N, request.nitrogenMl());
        ml.put(Nutrient.P, request.phosphorusMl());
        ml.put(Nutrient.K, request.potassiumMl());
        if (ml.containsValue(null)) {
            throw new BusinessRuleException("A manual dose needs millilitres for N, P and K (0 is allowed)",
                    Map.of("reason", "DOSE_MISSING"));
        }
        if (ml.values().stream().allMatch(v -> v.signum() == 0)) {
            throw new BusinessRuleException("A manual dose of 0 ml for N, P and K pours nothing",
                    Map.of("reason", "DOSE_ZERO"));
        }
        for (Map.Entry<Nutrient, BigDecimal> entry : ml.entrySet()) {
            if (entry.getValue().signum() == 0) {
                continue;
            }
            String code = code(entry.getKey());
            Actuator pump = actuatorRepository.findFirstByCode(code).filter(Actuator::isEnabled).orElse(null);
            if (pump == null) {
                throw new BusinessRuleException("Dosing pump %s is missing or disabled".formatted(code),
                        details("PUMP_UNAVAILABLE", entry.getKey(), code));
            }
            BigDecimal flow = flow(pump).orElse(null);
            if (flow == null) {
                throw new BusinessRuleException("Dosing pump %s has no flow calibration".formatted(code),
                        details(PUMP_NOT_CALIBRATED, entry.getKey(), code));
            }
            BigDecimal max = maxMl(pump, flow);
            if (max != null && entry.getValue().compareTo(max) > 0) {
                Map<String, Object> details = details("TOO_MUCH", entry.getKey(), code);
                details.put("maxMl", max);
                throw new BusinessRuleException(
                        "%s ml is more than pump %s pours in one run (%s ml)".formatted(entry.getValue(), code, max),
                        details);
            }
        }
    }

    private static Map<String, Object> details(String reason, Nutrient nutrient, String actuatorCode) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", reason);
        details.put("nutrient", nutrient.name());
        details.put("actuatorCode", actuatorCode);
        return details;
    }

    private FertilizationDoseResponse toResponse(FertilizationDoseSetting setting) {
        List<FertilizationDoseResponse.Pump> pumps = new ArrayList<>();
        for (Nutrient nutrient : Nutrient.values()) {
            String code = code(nutrient);
            Optional<Actuator> pump = actuatorRepository.findFirstByCode(code);
            BigDecimal flow = pump.flatMap(this::flow).orElse(null);
            pumps.add(new FertilizationDoseResponse.Pump(
                    nutrient.name(),
                    code,
                    pump.map(Actuator::isEnabled).orElse(false),
                    flow,
                    pump.isPresent() && flow != null ? maxMl(pump.get(), flow) : null));
        }
        if (setting == null) {
            return new FertilizationDoseResponse(FertilizationDoseSetting.Mode.AI, null, null, null, null, null, pumps);
        }
        return new FertilizationDoseResponse(setting.getMode(), setting.getNitrogenMl(), setting.getPhosphorusMl(),
                setting.getPotassiumMl(), setting.getUpdatedAt(), setting.getUpdatedBy(), pumps);
    }

    private Optional<BigDecimal> flow(Actuator pump) {
        return calibrations.currentCalibration(pump.getId())
                .map(ActuatorCalibration::getFlowMlPerSecond)
                .filter(f -> f.signum() > 0);
    }

    /** Paling banyak ml dalam satu jalan, dibulatkan ke bawah agar tidak melewati batas pompa. */
    private static BigDecimal maxMl(Actuator pump, BigDecimal flow) {
        Integer max = pump.getMaxRunSeconds();
        return max == null ? null : flow.multiply(BigDecimal.valueOf(max)).setScale(0, RoundingMode.DOWN);
    }

    private String code(Nutrient nutrient) {
        return switch (nutrient) {
            case N -> properties.actuators().nitrogen();
            case P -> properties.actuators().phosphorus();
            case K -> properties.actuators().potassium();
        };
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
