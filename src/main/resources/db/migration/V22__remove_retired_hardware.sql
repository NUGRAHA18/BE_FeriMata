-- Hapus sensor dan aktuator yang sudah pensiun karena kesepakatan dokumen, bukan karena dimatikan operator.
--
-- V6 (Revisi A, 21 Okt 2026) dan V9 (dosing pindah ke tim AI, 28 Okt 2026) hanya menonaktifkannya supaya
-- riwayat tetap ada. Akibatnya semuanya masih tampil di Pengaturan → "Sensor & perangkat yang dimatikan"
-- dan bisa dikira perangkat yang perlu dinyalakan lagi. Hardware-nya tidak ada, dan riwayatnya hanya dari
-- masa simulator, jadi baris beserta riwayatnya dihapus.
--
-- Hanya baris yang masih nonaktif yang dihapus: kalau ada yang sengaja dinyalakan lagi, dibiarkan.
-- Sensor/perangkat yang dimatikan operator sendiri atau yang terdaftar otomatis tidak tersentuh.

CREATE TEMPORARY TABLE retired_sensor ON COMMIT DROP AS
SELECT s.id
FROM sensor s
JOIN device d ON d.id = s.device_id
WHERE NOT s.enabled
  AND ((d.device_code = 'GREENHOUSE-01' AND s.code LIKE 'SOIL-B-%')
    OR (d.device_code = 'RIO-TANK-01' AND s.code IN ('FLOAT-SUPPLY', 'FLOAT-MIXING'))
    OR s.metric_key IN ('dosing.iteration', 'dosing.ec_target', 'dosing.ec_measured',
                        'dosing.n_ml', 'dosing.p_ml', 'dosing.k_ml'));

CREATE TEMPORARY TABLE retired_actuator ON COMMIT DROP AS
SELECT a.id
FROM actuator a
JOIN device d ON d.id = a.device_id
WHERE NOT a.enabled
  AND ((d.device_code = 'PANEL-01' AND a.code = 'LED-ILLUMINATION')
    OR (d.device_code = 'RIO-TANK-01' AND a.code IN ('FILL-VALVE', 'SAMPLING-PUMP'))
    OR a.code = 'DOSING-LOOP');

-- Peringatan lama tetap disimpan, hanya rujukannya dilepas.
UPDATE alert SET related_sensor_id = NULL WHERE related_sensor_id IN (SELECT id FROM retired_sensor);
UPDATE alert SET related_actuator_id = NULL WHERE related_actuator_id IN (SELECT id FROM retired_actuator);

DELETE FROM sensor_reading WHERE sensor_id IN (SELECT id FROM retired_sensor);
DELETE FROM sensor_reading_hourly WHERE sensor_id IN (SELECT id FROM retired_sensor);
DELETE FROM actuator_command WHERE actuator_id IN (SELECT id FROM retired_actuator);

-- Tabel kalibrasi (V7) ikut terhapus lewat ON DELETE CASCADE.
DELETE FROM sensor WHERE id IN (SELECT id FROM retired_sensor);
DELETE FROM actuator WHERE id IN (SELECT id FROM retired_actuator);
