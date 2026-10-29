package com.fertimata.backend.irrigation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Penyiraman terjadwal.
 *
 * @param enabled matikan untuk menghentikan semua jadwal tanpa menghapusnya (mis. saat komisioning)
 * @param actuatorCode pompa yang dinyalakan; kode aktuator di katalog hardware
 * @param checkInterval jeda pemeriksaan jadwal; jadwal dimulai paling lambat sebesar ini setelah jamnya
 * @param grace jadwal yang terlewat lebih lama dari ini (backend mati) tidak dikejar, dicatat MISSED
 */
@Validated
@ConfigurationProperties(prefix = "app.irrigation")
public record IrrigationProperties(
        boolean enabled, @NotBlank String actuatorCode, @NotNull Duration checkInterval, @NotNull Duration grace) {}
