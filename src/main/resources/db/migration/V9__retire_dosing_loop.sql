-- Campur pupuk otomatis dan jadwal siram/dosing dipindah ke layanan terpisah tim AI (28 Okt 2026).
--
-- Layanan itu memakai pompa DOSING-N/P/K, STIRRER dan DIST-PUMP lewat API perintah biasa, jadi aktuator
-- virtual DOSING-LOOP (BE-1) dan metrik iterasinya (BE-4) tidak dipakai lagi. Katalog tidak menghapus
-- baris yang hilang darinya (riwayat masih merujuknya), maka di sini cukup dinonaktifkan: perintah ke
-- DOSING-LOOP ditolak dan sensor dosing.* hilang dari dashboard.
UPDATE actuator
SET enabled = FALSE, updated_at = now()
WHERE code = 'DOSING-LOOP' AND enabled;

UPDATE sensor
SET enabled = FALSE, updated_at = now()
WHERE metric_key IN ('dosing.iteration', 'dosing.ec_target', 'dosing.ec_measured',
                     'dosing.n_ml', 'dosing.p_ml', 'dosing.k_ml')
  AND enabled;
