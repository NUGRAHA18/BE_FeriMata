-- Keputusan 29 Okt 2026: aplikasi difokuskan ke kontrol untuk petani; fitur riset dihapus sampai ke
-- akarnya. Kode untuk tabel/kolom di bawah ini sudah tidak ada, jadi datanya ikut dihapus.
--   - pengamatan tanaman (V4) dan catatan panen (V7): UI dan endpoint dihapus
--   - automation_rule (V1): mesin otomasi tidak pernah aktif; penyiraman terjadwal ada di irrigation_schedule (V13)
--   - atribut riset tanaman (V4): perlakuan K0/N-/P-/K-, blok, baris, plot, stasiun, posisi, grup pembanding,
--     daun berlabel, metadata, dan keterikatan ke siklus (umur/fase kini berlaku untuk seluruh greenhouse)
--   - rasio N:P:K per fase (V4): target N/P/K milik fuzzy tim AI
DROP TABLE IF EXISTS plant_observation;
DROP TABLE IF EXISTS harvest_record;
DROP TABLE IF EXISTS automation_rule;

DROP INDEX IF EXISTS idx_plant_cycle_treatment;
DROP INDEX IF EXISTS idx_plant_station;
ALTER TABLE plant
    DROP CONSTRAINT IF EXISTS fk_plant_cycle,
    DROP COLUMN IF EXISTS cycle_id,
    DROP COLUMN IF EXISTS treatment_code,
    DROP COLUMN IF EXISTS block_code,
    DROP COLUMN IF EXISTS row_code,
    DROP COLUMN IF EXISTS plot_code,
    DROP COLUMN IF EXISTS station_code,
    DROP COLUMN IF EXISTS position_in_row,
    DROP COLUMN IF EXISTS comparison_group,
    DROP COLUMN IF EXISTS tagged_leaves,
    DROP COLUMN IF EXISTS metadata;

ALTER TABLE growth_phase
    DROP COLUMN IF EXISTS ratio_n,
    DROP COLUMN IF EXISTS ratio_p,
    DROP COLUMN IF EXISTS ratio_k;
