# FertiMata — Backend

Backend Spring Boot untuk greenhouse melon: menerima telemetri dari edge lewat MQTT, menyimpan ke PostgreSQL,
mengirim perintah aktuator dengan aturan keselamatan, jadwal siram, kalibrasi, dan meneruskan perubahan ke PWA lewat WebSocket.

```
PWA ──REST/STOMP──► Spring Boot ──MQTT──► edge (Jetson) ──► hardware
                         │
                         ▼ PostgreSQL
```

Java 17 · Spring Boot 4 · JPA + Flyway · Spring Integration MQTT · STOMP · JWT · Testcontainers.

## Menjalankan

```bash
cp .env.example .env          # isi JWT_SECRET (wajib, 32+ karakter: openssl rand -base64 48)
docker compose up --build     # postgres + mosquitto + backend
# atau lokal:
docker compose up -d postgres mosquitto
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev    # Windows: mvnw.cmd
```

Profil `dev` + `MQTT_ENABLED=false` jalan tanpa broker dan hardware. Akun pertama dari `BOOTSTRAP_OPERATOR_*`
(dev: `operator` / `dev-operator-password`). Swagger UI: http://localhost:8080/swagger-ui.html. Kontrak API: `openapi/`.

## Test

```bash
./mvnw test                        # unit, tanpa Docker
./mvnw test -Pintegration-tests    # integrasi, butuh Docker
```

## Struktur

`src/main/java/com/fertimata/backend/`: satu package per domain (`actuator`, `telemetry`, `mqtt`, `irrigation`,
`calibration`, `cultivation`, `alert`, `push`, …). Controller tipis, aturan di service, MQTT di balik interface.
Deploy Jetson: `deploy/` (Caddy, compose, Mosquitto).

Mosquitto bawaan menerima akses anonim; di luar laptop pakai kredensial & TLS (`MQTT_USERNAME`, `MQTT_PASSWORD`).
