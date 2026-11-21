-- Peran ADMIN dihapus: pemakainya satu-dua petani di kebun yang sama, jadi kemampuan kelola akun
-- diwariskan ke OPERATOR yang kini peran tertinggi. Peran disimpan sebagai teks (tanpa CHECK), cukup UPDATE.
UPDATE app_user SET role = 'OPERATOR' WHERE role = 'ADMIN';
