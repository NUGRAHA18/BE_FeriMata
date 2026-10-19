package com.fertimata.backend.telemetry.retention;

import com.fertimata.backend.telemetry.domain.SensorReadingRepository;
import com.fertimata.backend.telemetry.domain.TelemetryMessageRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Menghapus telemetri lama dalam batch kecil.
 *
 * <p>Setiap batch adalah transaksinya sendiri, sehingga penghapusan jutaan baris tidak pernah
 * menahan satu transaksi panjang dan ingest telemetri yang berjalan bersamaan tidak ikut tertahan.
 */
@Service
public class TelemetryRetentionService {

    private final SensorReadingRepository sensorReadingRepository;
    private final TelemetryMessageRepository telemetryMessageRepository;

    public TelemetryRetentionService(
            SensorReadingRepository sensorReadingRepository,
            TelemetryMessageRepository telemetryMessageRepository) {
        this.sensorReadingRepository = sensorReadingRepository;
        this.telemetryMessageRepository = telemetryMessageRepository;
    }

    /** @return jumlah baris {@code sensor_reading} yang dihapus pada batch ini */
    @Transactional
    public int purgeReadingsBatch(Instant cutoff, int batchSize) {
        return sensorReadingRepository.deleteBatchRecordedBefore(cutoff, batchSize);
    }

    /** @return jumlah baris {@code telemetry_message} yang dihapus pada batch ini */
    @Transactional
    public int purgeMessagesBatch(Instant cutoff, int batchSize) {
        return telemetryMessageRepository.deleteBatchReceivedBefore(cutoff, batchSize);
    }
}
