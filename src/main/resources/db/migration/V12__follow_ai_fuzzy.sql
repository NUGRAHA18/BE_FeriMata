-- Keputusan 29 Okt 2026: yang sudah ada di kode tim AI, diikuti (Ai-code/app/ai/fuzzy_fertilizer).
-- Katalog hardware hanya membuat baris baru, jadi database yang sudah berjalan disesuaikan di sini.

-- 1. Fase tanam = FASE_MF fuzzy pupuk AI, dengan batas tegas di titik keanggotaan 0,5. Target EC = inti
--    himpunan "Optimal" fuzzy tingkat 2 (1,2-2,5 mS/cm) untuk semua fase. Rasio N:P:K dihapus: target
--    N/P/K mutlak milik fuzzy AI. Hanya siklus ACTIVE/PLANNED; riwayat siklus selesai tidak disentuh.
DELETE FROM growth_phase
WHERE cycle_id IN (SELECT id FROM planting_cycle WHERE status IN ('ACTIVE', 'PLANNED'));

INSERT INTO growth_phase (cycle_id, code, name, start_hst, end_hst, ec_target_min, ec_target_max, created_at, updated_at)
SELECT c.id, p.code, p.name, p.start_hst, p.end_hst, 1.2, 2.5, now(), now()
FROM planting_cycle c
CROSS JOIN (VALUES
    ('SEMAI', 'Semai', 0, 7),
    ('VEGETATIF', 'Vegetatif', 8, 24),
    ('PEMBUNGAAN', 'Pembungaan', 25, 34),
    ('PEMBUAHAN', 'Pembuahan', 35, 60),
    ('PEMATANGAN', 'Pematangan', 61, 74),
    ('PANEN', 'Panen', 75, NULL)
) AS p (code, name, start_hst, end_hst)
WHERE c.status IN ('ACTIVE', 'PLANNED');

COMMENT ON COLUMN growth_phase.ec_target_min IS 'mS/cm pada 25 C. Sejak V12 mengikuti fuzzy AI: 1,2-2,5 untuk semua fase.';

-- 2. Batas lama nyala pompa pupuk = batas atas keluaran fuzzy pupuk AI (0-150 s). Hanya baris yang
--    masih bernilai bawaan katalog lama (120 s); nilai yang sudah diubah operator dibiarkan.
UPDATE actuator
SET max_run_seconds = 150, updated_at = now()
WHERE type = 'DOSING_PUMP' AND max_run_seconds = 120;

-- 3. EC tanah dalam mS/cm, satuan semesta fuzzy AI (0-4). Edge membagi nilai register dengan 1000.
--    Bacaan lama (bila ada) tetap tersimpan dengan satuannya sendiri di sensor_reading.unit.
UPDATE sensor
SET unit = 'mS/cm', updated_at = now()
WHERE metric_key = 'soil_a.ec' AND unit = 'uS/cm';
