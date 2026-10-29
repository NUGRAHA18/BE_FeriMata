-- Kode alasan putaran jadwal siram, supaya aplikasi menulis alasannya dalam bahasa petani tanpa
-- mencocokkan teks: kode interlock (BACKUP_POWER, FLOAT_LOW, EXCLUSIVE_OPERATION, BLOCKED_WHILE_ACTIVE,
-- DEVICE_NOT_ONLINE), kode error API, PUMP_UNAVAILABLE, atau BACKEND_DOWN (jadwal terlewat).
ALTER TABLE irrigation_schedule ADD COLUMN last_reason_code VARCHAR(64);
