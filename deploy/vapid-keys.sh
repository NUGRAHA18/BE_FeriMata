#!/usr/bin/env bash
# Membuat sepasang kunci VAPID untuk Web Push hanya dengan openssl (Node/npx tidak ada di Jetson).
# Hasilnya sama formatnya dengan `npx web-push generate-vapid-keys`: base64url tanpa padding,
# publik = titik P-256 tak terkompresi 65 byte, privat = 32 byte.
#
#   bash vapid-keys.sh            # lalu salin dua baris hasilnya ke .env
#
# Buat SEKALI. Mengganti kunci memutus semua langganan notifikasi yang sudah ada.
set -euo pipefail

b64url() { base64 | tr -d '=\n' | tr '/+' '_-'; }

pem="$(openssl ecparam -name prime256v1 -genkey -noout)"
# DER SEC1 ECPrivateKey: 30 77 02 01 01 04 20 <32 byte kunci privat> ...
private="$(printf '%s\n' "$pem" | openssl ec -outform DER 2>/dev/null | tail -c +8 | head -c 32 | b64url)"
# DER SubjectPublicKeyInfo P-256 berakhir dengan titik 04||x||y (65 byte).
public="$(printf '%s\n' "$pem" | openssl ec -pubout -outform DER 2>/dev/null | tail -c 65 | b64url)"

if [ "${#private}" -ne 43 ] || [ "${#public}" -ne 87 ]; then
  echo "Gagal membuat kunci (panjang tidak sesuai)" >&2
  exit 1
fi

echo "VAPID_PUBLIC_KEY=$public"
echo "VAPID_PRIVATE_KEY=$private"
