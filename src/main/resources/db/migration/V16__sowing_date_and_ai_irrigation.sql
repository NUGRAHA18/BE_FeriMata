-- 31 Okt 2026, keputusan pemilik proyek:
-- 1. HST hari ke-0 = tanggal SEMAI (bukan tanam pindah). Nilai yang sudah tersimpan dipakai apa adanya;
--    operator memperbaikinya lewat Pengaturan bila yang dulu diisi adalah tanggal tanam pindah.
-- 2. Durasi siram diambil dari fuzzy irigasi layanan AI saat jadwal berjalan; duration_seconds jadwal
--    menjadi durasi cadangan bila layanan AI tidak menjawab atau datanya belum siap.

ALTER TABLE planting_cycle RENAME COLUMN transplant_date TO sowing_date;
COMMENT ON COLUMN planting_cycle.sowing_date IS 'Tanggal semai = HST hari ke-0 (zona waktu Asia/Jakarta).';
COMMENT ON TABLE planting_cycle IS 'Satu musim tanam. HST = hari sejak sowing_date (zona waktu Asia/Jakarta).';

ALTER TABLE irrigation_schedule ADD COLUMN last_duration_seconds INTEGER;
ALTER TABLE irrigation_schedule ADD COLUMN last_duration_source VARCHAR(16);
COMMENT ON COLUMN irrigation_schedule.duration_seconds IS
    'Durasi cadangan: dipakai bila fuzzy AI tidak bisa dipakai, atau selalu bila app.irrigation.duration-source=MANUAL.';
COMMENT ON COLUMN irrigation_schedule.last_duration_seconds IS 'Durasi yang benar-benar dikirim ke pompa pada jalan terakhir.';
COMMENT ON COLUMN irrigation_schedule.last_duration_source IS 'AI, FALLBACK (AI gagal, pakai durasi cadangan) atau MANUAL.';
