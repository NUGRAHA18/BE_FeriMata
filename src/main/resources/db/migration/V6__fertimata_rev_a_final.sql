-- FERTIMATA Revisi A final (Arsitektur-Sistem.md, 21 Oktober 2026).
--
-- Katalog hardware hanya create-only, jadi database yang dibuat dari katalog versi 17 September tidak
-- ikut berubah saat fertimata-rev-a.yml diperbarui. Migrasi ini menyelaraskan baris lama itu:
--   * baris yang perangkatnya sudah tidak ada dinonaktifkan, tidak dihapus - riwayat masih merujuknya;
--   * baris yang maknanya berubah diganti nama/keterangannya di tempat, supaya riwayatnya tetap nyambung;
--   * perangkat baru (pan kamera, pompa aduk, daya papan EC, suhu larutan, float LOW) TIDAK dibuat di
--     sini: sinkronisasi katalog saat startup membuatnya karena barisnya belum ada.
-- Pada database kosong semua UPDATE di bawah tidak mengenai baris apa pun.

-- ---------------------------------------------------------------------------
-- Device
-- ---------------------------------------------------------------------------
UPDATE device
SET name        = 'PZEM-016 (PLN & arus panel)',
    description = 'Di bus RS485 bersama, alamat 30. Diam sementara relay 20 menjawab = PLN padam.',
    metadata    = '{"bus": "RS485 /dev/rs485 9600 8N1", "modbusAddress": "30", "ct": "Jalur L utama panel (arus total panel, bukan pompa saja)"}'::jsonb,
    updated_at  = now()
WHERE device_code = 'PZEM-01';

UPDATE device
SET description = 'Relay 4-ch di panel box.',
    updated_at  = now()
WHERE device_code = 'PANEL-01';

UPDATE device
SET name        = 'Sensor greenhouse (alamat 1, 3, 4)',
    description = 'Sensor tanah, udara dan cahaya pada bus RS485 daisy chain.',
    updated_at  = now()
WHERE device_code = 'GREENHOUSE-01';

UPDATE device
SET name        = 'Kotak tangki fertigasi',
    description = 'Kotak IP56 di bibir drum - relay 8-ch alamat 21, ADC 7-ch alamat 10, DS18B20 alamat 6.',
    metadata    = COALESCE(metadata, '{}'::jsonb) || '{"modbusAddress": "6, 10, 21"}'::jsonb,
    updated_at  = now()
WHERE device_code = 'RIO-TANK-01';

UPDATE device
SET metadata   = COALESCE(metadata, '{}'::jsonb)
                     || '{"location": "Panel box (lokasi akhir belum terverifikasi)", "power": "Buck 18 V + fuse 4 A + TVS, mode daya 15 W, panel tanpa fan"}'::jsonb,
    updated_at = now()
WHERE device_code = 'JETSON-01';

-- ---------------------------------------------------------------------------
-- Sensor: PZEM sekarang mengukur arus total panel, bukan pompa saja
-- ---------------------------------------------------------------------------
UPDATE sensor s
SET code        = 'MAINS-' || substring(s.code FROM 6),
    metric_key  = 'mains.' || substring(s.metric_key FROM 6),
    name        = CASE s.code
                      WHEN 'PUMP-CURRENT' THEN 'Arus total panel'
                      WHEN 'PUMP-POWER' THEN 'Daya total panel'
                      WHEN 'PUMP-ENERGY' THEN 'Energi total panel'
                      ELSE 'Faktor daya panel'
                  END,
    description = CASE s.code
                      WHEN 'PUMP-CURRENT' THEN 'Baseline saat pompa OFF kira-kira 0,3 A. Di atas baseline saat pompa OFF = SSR rusak short.'
                      ELSE NULL
                  END,
    updated_at  = now()
FROM device d
WHERE s.device_id = d.id
  AND d.device_code = 'PZEM-01'
  AND s.code IN ('PUMP-CURRENT', 'PUMP-POWER', 'PUMP-ENERGY', 'PUMP-POWER-FACTOR');

-- ---------------------------------------------------------------------------
-- Sensor: yang tidak ada lagi di Revisi A
-- ---------------------------------------------------------------------------
-- Sensor tanah kedua (alamat 2) dan dua float lama di kanal ADC; float sekarang satu, di IN1 relay 21.
UPDATE sensor s
SET enabled     = FALSE,
    description = 'Dihapus di Revisi A (21 Okt 2026). Disimpan untuk riwayat.',
    updated_at  = now()
FROM device d
WHERE s.device_id = d.id
  AND ((d.device_code = 'GREENHOUSE-01' AND s.code LIKE 'SOIL-B-%')
    OR (d.device_code = 'RIO-TANK-01' AND s.code IN ('FLOAT-SUPPLY', 'FLOAT-MIXING')));

-- ---------------------------------------------------------------------------
-- Sensor: keterangan yang berubah
-- ---------------------------------------------------------------------------
UPDATE sensor s
SET name       = replace(s.name, ' plot A', ''),
    updated_at = now()
FROM device d
WHERE s.device_id = d.id
  AND d.device_code = 'GREENHOUSE-01'
  AND s.code LIKE 'SOIL-A-%'
  AND s.name LIKE '% plot A';

UPDATE sensor s
SET metadata   = COALESCE(s.metadata, '{}'::jsonb) || '{"model": "SHT40 XY-MD02"}'::jsonb,
    updated_at = now()
FROM device d
WHERE s.device_id = d.id
  AND d.device_code = 'GREENHOUSE-01'
  AND s.code IN ('AIR-TEMP', 'AIR-HUMIDITY');

UPDATE sensor s
SET description = 'Sensor cahaya RS485 0-200.000 lux, menghadap atas.',
    updated_at  = now()
FROM device d
WHERE s.device_id = d.id
  AND d.device_code = 'GREENHOUSE-01'
  AND s.code = 'LIGHT';

UPDATE sensor s
SET name        = 'EC larutan',
    description = 'DFR0300 di rakit probe, dikompensasi ke 25 C.',
    metadata    = COALESCE(s.metadata, '{}'::jsonb) || '{"channel": "A1"}'::jsonb,
    updated_at  = now()
FROM device d
WHERE s.device_id = d.id
  AND d.device_code = 'RIO-TANK-01'
  AND s.code = 'TANK-EC';

UPDATE sensor s
SET name        = 'pH larutan',
    description = 'PH-4502C di rakit probe, dibaca saat papan EC mati.',
    metadata    = COALESCE(s.metadata, '{}'::jsonb) || '{"channel": "A2"}'::jsonb,
    updated_at  = now()
FROM device d
WHERE s.device_id = d.id
  AND d.device_code = 'RIO-TANK-01'
  AND s.code = 'TANK-PH';

UPDATE sensor s
SET name        = CASE s.code WHEN 'TROLLEY-HOME' THEN 'Limit HOME trolley (selatan)' ELSE 'Limit END trolley (utara)' END,
    description = 'Kontak NC - aktif berarti tertekan ATAU kabel putus.',
    updated_at  = now()
FROM device d
WHERE s.device_id = d.id
  AND d.device_code = 'JETSON-01'
  AND s.code IN ('TROLLEY-HOME', 'TROLLEY-END');

UPDATE sensor s
SET description = 'Nomor stasiun 1-10 dari hitungan magnet reed (tiap 600 mm); 0 = HOME.',
    updated_at  = now()
FROM device d
WHERE s.device_id = d.id
  AND d.device_code = 'JETSON-01'
  AND s.code = 'TROLLEY-STATION';

-- ---------------------------------------------------------------------------
-- Actuator: yang tidak ada lagi di Revisi A
-- ---------------------------------------------------------------------------
-- LED dihapus (R1 sekarang pan kamera), solenoid pengisian sudah otomatis, flow cell dan pompa
-- sampling diganti rakit probe di permukaan drum.
UPDATE actuator a
SET enabled     = FALSE,
    description = 'Dihapus di Revisi A (21 Okt 2026). Disimpan untuk riwayat.',
    metadata    = COALESCE(a.metadata, '{}'::jsonb) - 'channel',
    updated_at  = now()
FROM device d
WHERE a.device_id = d.id
  AND ((d.device_code = 'PANEL-01' AND a.code = 'LED-ILLUMINATION')
    OR (d.device_code = 'RIO-TANK-01' AND a.code IN ('FILL-VALVE', 'SAMPLING-PUMP')));

-- ---------------------------------------------------------------------------
-- Actuator: batas durasi. Timer mekanis dihapus, jadi batas software sekarang lapisan utama (§5.4).
-- Nilai yang sudah diubah operator lewat API dibiarkan; hanya yang kosong atau masih nilai lama diisi.
-- ---------------------------------------------------------------------------
UPDATE actuator a
SET max_run_seconds = 900,
    description     = 'Lewat SSR-40DA. Tanpa timer mekanis - batas durasi dari software (§5.4).',
    updated_at      = now()
FROM device d
WHERE a.device_id = d.id
  AND d.device_code = 'PANEL-01'
  AND a.code = 'DIST-PUMP'
  AND a.max_run_seconds IS NULL;

UPDATE actuator a
SET max_run_seconds = 60,
    description     = 'Rel 6 m pada 0,15 m/s, perjalanan penuh kira-kira 40 detik.',
    updated_at      = now()
FROM device d
WHERE a.device_id = d.id
  AND d.device_code = 'PANEL-01'
  AND a.code = 'TROLLEY-RUN'
  AND (a.max_run_seconds IS NULL OR a.max_run_seconds = 120);

UPDATE actuator a
SET description = 'ON = utara/END, OFF = selatan/HOME. Jangan dibalik saat motor berjalan - diurutkan oleh edge agent.',
    updated_at  = now()
FROM device d
WHERE a.device_id = d.id
  AND d.device_code = 'PANEL-01'
  AND a.code = 'TROLLEY-DIR';

UPDATE actuator a
SET max_run_seconds = 120,
    updated_at      = now()
FROM device d
WHERE a.device_id = d.id
  AND d.device_code = 'RIO-TANK-01'
  AND a.code IN ('DOSING-N', 'DOSING-P', 'DOSING-K')
  AND a.max_run_seconds IS NULL;

UPDATE actuator a
SET name       = CASE a.code
                     WHEN 'DOSING-N' THEN 'Pompa dosing N (Calnit)'
                     WHEN 'DOSING-P' THEN 'Pompa dosing P (MKP)'
                     ELSE 'Pompa dosing K (SOP)'
                 END,
    updated_at = now()
FROM device d
WHERE a.device_id = d.id
  AND d.device_code = 'RIO-TANK-01'
  AND a.code IN ('DOSING-N', 'DOSING-P', 'DOSING-K')
  AND a.name IN ('Pompa dosing N', 'Pompa dosing P', 'Pompa dosing K');

COMMENT ON COLUMN actuator.max_run_seconds IS 'Upper bound for durationSeconds on an activating command. Rev A has no mechanical timer, so this is the primary limit. Null means no software limit.';

-- ---------------------------------------------------------------------------
-- AI: setiap stasiun dipotret dua arah
-- ---------------------------------------------------------------------------
ALTER TABLE ai_detection
    ADD COLUMN pan_direction VARCHAR(16);

COMMENT ON COLUMN ai_detection.pan_direction IS 'Arah kamera saat memotret: TIMUR atau BARAT. Disimpan seperti dilaporkan, huruf besar.';
COMMENT ON COLUMN ai_detection.station_code IS 'Stasiun kamera, ST-01..ST-10 pada Revisi A. Teks bebas supaya tata letak baru tidak butuh migrasi.';

-- ---------------------------------------------------------------------------
-- Tanaman
-- ---------------------------------------------------------------------------
COMMENT ON COLUMN plant.plot_code IS 'Sensor tanah yang mewakili galon ini: A -> soil_a.*. Revisi A hanya punya satu sensor tanah; kosong berarti A.';
