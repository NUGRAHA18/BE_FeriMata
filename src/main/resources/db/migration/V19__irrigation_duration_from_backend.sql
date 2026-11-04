-- 4 Nov 2026, keputusan bersama tim AI: lama siram ditentukan backend (durasi jadwal isian petani),
-- bukan fuzzy irigasi layanan AI. Membatalkan butir 2 di V16.

ALTER TABLE irrigation_schedule DROP COLUMN last_duration_source;
COMMENT ON COLUMN irrigation_schedule.duration_seconds IS 'Lama pompa siram menyala setiap jadwal ini berjalan.';
