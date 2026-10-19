-- Retensi telemetri.
--
-- Sebelum ini `telemetry_message` (payload mentah, jsonb) dan `sensor_reading` tumbuh tanpa batas: pada
-- interval 10 detik dengan 31 metrik itu ratusan ribu baris per hari. Dua hal diubah di sini:
--
--   1. `sensor_reading.telemetry_message_id` boleh NULL dan mengikuti ON DELETE SET NULL, sehingga payload
--      mentah bisa dibuang lebih cepat (beberapa hari) daripada pembacaan ternormalisasi (puluhan hari)
--      tanpa melanggar foreign key. Pembacaan tetap utuh; hanya tautan ke payload asalnya yang hilang.
--   2. Indeks pada kolom waktu, agar pekerjaan pembersihan berkala (TelemetryRetentionJob) tidak
--      memindai seluruh tabel.

ALTER TABLE sensor_reading
    ALTER COLUMN telemetry_message_id DROP NOT NULL;

ALTER TABLE sensor_reading
    DROP CONSTRAINT fk_reading_message;

ALTER TABLE sensor_reading
    ADD CONSTRAINT fk_reading_message
        FOREIGN KEY (telemetry_message_id) REFERENCES telemetry_message (id) ON DELETE SET NULL;

COMMENT ON COLUMN sensor_reading.telemetry_message_id IS
    'Payload mentah asal pembacaan ini. NULL setelah payload mentah dibersihkan oleh kebijakan retensi.';

CREATE INDEX idx_reading_recorded ON sensor_reading (recorded_at);
CREATE INDEX idx_telemetry_received ON telemetry_message (received_at);
