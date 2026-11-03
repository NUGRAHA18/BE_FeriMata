-- Nama katalog dalam bahasa petani (permintaan FE 3 Nov 2026). Hanya kolom name; code dan type tetap,
-- karena edge, interlock dan layanan AI memakai kode. Katalog YAML hanya membuat baris yang belum ada,
-- jadi nama di basis data yang sudah berjalan harus diganti di sini.
UPDATE device SET name = 'Komputer utama' WHERE device_code = 'JETSON-01';
UPDATE device SET name = 'Meteran listrik panel' WHERE device_code = 'PZEM-01';
UPDATE device SET name = 'Panel saklar pompa & kamera' WHERE device_code = 'PANEL-01';
UPDATE device SET name = 'Sensor udara & tanah greenhouse' WHERE device_code = 'GREENHOUSE-01';
UPDATE device SET name = 'Kotak kendali drum pupuk' WHERE device_code = 'RIO-TANK-01';

UPDATE sensor SET name = 'Suhu komputer utama' WHERE code = 'JETSON-SOC-TEMP';
UPDATE sensor SET name = 'Sensor ujung rel selatan (posisi awal)' WHERE code = 'TROLLEY-HOME';
UPDATE sensor SET name = 'Sensor ujung rel utara' WHERE code = 'TROLLEY-END';
UPDATE sensor SET name = 'Posisi kereta kamera' WHERE code = 'TROLLEY-STATION';
UPDATE sensor SET name = 'Pelampung air drum' WHERE code = 'FLOAT-LOW';

UPDATE actuator SET name = 'Pompa siram' WHERE code = 'DIST-PUMP';
UPDATE actuator SET name = 'Kereta kamera (jalan)' WHERE code = 'TROLLEY-RUN';
UPDATE actuator SET name = 'Kereta kamera (arah)' WHERE code = 'TROLLEY-DIR';
UPDATE actuator SET name = 'Arah kamera (timur/barat)' WHERE code = 'CAMERA-PAN';
UPDATE actuator SET name = 'Pindahkan kereta kamera' WHERE code = 'TROLLEY-GOTO';
UPDATE actuator SET name = 'Pengaduk drum pupuk' WHERE code = 'STIRRER';
UPDATE actuator SET name = 'Pompa pupuk N (Calnit)' WHERE code = 'DOSING-N';
UPDATE actuator SET name = 'Pompa pupuk P (MKP)' WHERE code = 'DOSING-P';
UPDATE actuator SET name = 'Pompa pupuk K (SOP)' WHERE code = 'DOSING-K';

-- Kalibrasi sensor tidak dipakai lagi; pengingat uji drift yang masih terbuka ditutup (tanpa pengguna).
UPDATE alert SET acknowledged = TRUE, acknowledged_at = NOW()
WHERE type = 'DRIFT_CHECK_DUE' AND acknowledged = FALSE;
