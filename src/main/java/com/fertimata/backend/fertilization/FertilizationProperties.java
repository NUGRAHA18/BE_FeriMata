package com.fertimata.backend.fertilization;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Pemupukan otomatis (keputusan 2 Nov 2026). Jarak hari dan dosis dari layanan AI; backend hanya menjalankan.
 *
 * @param enabled false = sesi siram tidak pernah didahului pemupukan
 * @param checkInterval seberapa sering langkah berikutnya (pompa P, K, selesai aduk) diperiksa
 * @param mixSeconds lama aduk setelah pompa K selesai
 * @param pumpGap jeda antar pompa dosing (N -> P -> K)
 * @param settle jeda setelah aduk selesai sebelum pompa siram, agar edge sempat melaporkan pompa aduk OFF
 *     (aturan keselamatan menolak pompa siram selama pompa aduk ON)
 * @param stepTimeout batas menunggu ACK dan laporan OFF pompa sebelumnya sebelum dosing dihentikan
 * @param grace langkah yang terlambat lebih dari ini (backend mati) tidak dikejar
 */
@ConfigurationProperties(prefix = "app.fertilization")
public record FertilizationProperties(
        Boolean enabled,
        Duration checkInterval,
        Integer mixSeconds,
        Duration pumpGap,
        Duration settle,
        Duration stepTimeout,
        Duration grace,
        Actuators actuators) {

    /** Kode aktuator di katalog hardware. */
    public record Actuators(String nitrogen, String phosphorus, String potassium, String stirrer) {
        public Actuators {
            nitrogen = nitrogen == null ? "DOSING-N" : nitrogen;
            phosphorus = phosphorus == null ? "DOSING-P" : phosphorus;
            potassium = potassium == null ? "DOSING-K" : potassium;
            stirrer = stirrer == null ? "STIRRER" : stirrer;
        }
    }

    public FertilizationProperties {
        enabled = enabled == null || enabled;
        checkInterval = checkInterval == null ? Duration.ofSeconds(5) : checkInterval;
        mixSeconds = mixSeconds == null ? 120 : mixSeconds;
        pumpGap = pumpGap == null ? Duration.ofSeconds(5) : pumpGap;
        settle = settle == null ? Duration.ofSeconds(15) : settle;
        stepTimeout = stepTimeout == null ? Duration.ofSeconds(90) : stepTimeout;
        grace = grace == null ? Duration.ofMinutes(15) : grace;
        actuators = actuators == null ? new Actuators(null, null, null, null) : actuators;
    }
}
