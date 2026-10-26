-- Kode alasan dari ACK gagal edge (BE-7, Kontrak MQTT §4.3).
--
-- Edge menulis errorMessage sebagai "KODE: teks untuk petani", mis. "INTERLOCK_POWER: listrik PLN padam".
-- Kodenya disimpan terpisah supaya dashboard bisa memfilter atau memberi ikon tanpa mengurai teks.
-- error_message tetap menyimpan pesan utuh untuk audit. NULL = kegagalan tanpa kode (mis. gagal publish
-- di backend, atau perangkat lama yang tidak memakai format kode).
ALTER TABLE actuator_command ADD COLUMN failure_code VARCHAR(64);
