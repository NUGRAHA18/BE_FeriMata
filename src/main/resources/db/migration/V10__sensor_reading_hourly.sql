-- Ringkasan per jam untuk grafik jangka panjang.
--
-- sensor_reading dihapus setelah app.telemetry.retention.readings-max-age (30 hari), sehingga grafik
-- musim tanam penuh (~75 hari) tidak mungkin dari data mentah. Tabel ini menyimpan count/sum/min/max
-- per sensor per jam UTC dan tidak ikut dihapus retensi: ~24 baris per sensor per hari.
--
-- sum, bukan avg: rata-rata beberapa jam (grafik per hari/minggu) = sum(value_sum) / sum(sample_count),
-- tepat, tanpa bias jam yang sampelnya sedikit.
CREATE TABLE sensor_reading_hourly (
    sensor_id    BIGINT         NOT NULL,
    bucket_start TIMESTAMPTZ    NOT NULL,
    sample_count INTEGER        NOT NULL,
    value_sum    NUMERIC(24, 6) NOT NULL,
    value_min    NUMERIC(18, 6) NOT NULL,
    value_max    NUMERIC(18, 6) NOT NULL,
    updated_at   TIMESTAMPTZ    NOT NULL,
    CONSTRAINT pk_sensor_reading_hourly PRIMARY KEY (sensor_id, bucket_start),
    CONSTRAINT fk_reading_hourly_sensor FOREIGN KEY (sensor_id) REFERENCES sensor (id)
);

COMMENT ON TABLE sensor_reading_hourly IS 'Hourly numeric aggregates of sensor_reading; kept after raw readings are purged.';
COMMENT ON COLUMN sensor_reading_hourly.bucket_start IS 'Start of the UTC hour the readings were recorded in (recorded_at, not received_at).';

-- Job ringkasan menghitung ulang jam yang menerima pembacaan baru, termasuk kiriman terlambat dari
-- buffer edge yang recorded_at-nya sudah lama. Tanpa indeks ini job memindai seluruh tabel tiap putaran.
CREATE INDEX idx_reading_received ON sensor_reading (received_at);

-- Isi dari pembacaan yang masih ada, supaya grafik tidak kosong sampai job pertama berjalan.
INSERT INTO sensor_reading_hourly (sensor_id, bucket_start, sample_count, value_sum, value_min, value_max, updated_at)
SELECT sensor_id,
       date_trunc('hour', recorded_at AT TIME ZONE 'UTC') AT TIME ZONE 'UTC',
       count(*),
       sum(numeric_value),
       min(numeric_value),
       max(numeric_value),
       now()
FROM sensor_reading
WHERE numeric_value IS NOT NULL
GROUP BY 1, 2;
