# FertiMata — Backend

Backend for **FertiMata**, a smart-agriculture / IoT system for monitoring and controlling melon
cultivation (fertigation, soil and air sensing, camera trolley with AI nutrient-stress detection). This repository is the **phase 1 foundation**: a production-quality Spring Boot
backend that the hardware, AI and frontend work can all be built on top of.

The hardware configuration is **not final**. That constraint shaped the whole design: sensors,
actuators, command names, MQTT topics and thresholds are configuration or data, never code. See
[docs/architecture.md](docs/architecture.md) for the reasoning and for the list of decisions still
owed by the team.

---

## Contents

- [What it does](#what-it-does)
- [Stack](#stack)
- [Quick start](#quick-start)
- [Configuration](#configuration)
- [Database](#database)
- [MQTT](#mqtt)
- [API](#api)
- [Realtime](#realtime)
- [Development mode](#development-mode)
- [Testing](#testing)
- [Docker](#docker)
- [Project structure](#project-structure)
- [Current limitations](#current-limitations)
- [Open hardware decisions](#open-hardware-decisions)

---

## What it does

```
Next.js PWA  ──REST──────────►  Spring Boot  ──MQTT──►  Jetson / edge device  ──►  hardware
             ◄──WebSocket────                ◄────────  telemetry, status, AI results
                                     │
                                     ▼
                           PostgreSQL / Supabase
```

- Operators sign in and receive a JWT.
- Devices publish telemetry, heartbeats, actuator states and AI detections over MQTT.
- The backend validates and stores them, then pushes the change to the PWA over WebSocket.
- Operators command actuators through REST; the backend records who asked and what happened, then
  publishes the command to the device.

The frontend never talks to MQTT. The backend never talks to hardware.

---

## Stack

| | |
|---|---|
| Java | 17 |
| Spring Boot | 4.1.0 (Spring Framework 7, Spring Security 7) |
| Persistence | Spring Data JPA, Hibernate 7, PostgreSQL |
| Migrations | Flyway |
| Messaging | Spring Integration MQTT + Eclipse Paho v5 |
| Realtime | Spring WebSocket (STOMP) |
| Security | Spring Security + OAuth2 resource server (HS256 JWT via Nimbus) |
| Docs | springdoc-openapi (Swagger UI) |
| Tests | JUnit 5, Mockito, AssertJ, Testcontainers |
| Build | Maven (wrapper included) |

---

## Quick start

Nothing needs to be installed except a JDK 17+ and Docker (or your own PostgreSQL).

### 1. Configure

```bash
cp .env.example .env
```

Then set `JWT_SECRET` in `.env`. It is the one value with no default — the application refuses to
start without it.

```bash
openssl rand -base64 48        # any 32+ character random string works
```

### 2. Run everything with Docker

```bash
docker compose up --build
```

This starts PostgreSQL, a Mosquitto MQTT broker and the backend.

### 3. Or run the backend locally

Start just the infrastructure:

```bash
docker compose up -d postgres mosquitto
```

Then:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

On Windows use `mvnw.cmd`.

With `SPRING_PROFILES_ACTIVE=dev` and `MQTT_ENABLED=false` the backend runs **with no broker and no
hardware at all** — see [Development mode](#development-mode).

### 4. Sign in

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"operator","password":"dev-operator-password"}'
```

The first operator account is created on first start from `BOOTSTRAP_OPERATOR_*`, only while the
user table is empty. **Change the password after the first sign-in.**

Then open Swagger UI: <http://localhost:8080/swagger-ui.html>

---

## Configuration

Everything environment-specific is externalised. Nothing secret is committed; `.env` is gitignored
and `.env.example` documents every variable.

### Essential

| Variable | Default | Notes |
|---|---|---|
| `JWT_SECRET` | **none** | Required. 32+ characters. No fallback on purpose. |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/fertimata` | `SPRING_DATASOURCE_URL` also works |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | `fertimata` / empty | |
| `FRONTEND_URL` | `http://localhost:3000` | CORS origin; never a wildcard |
| `SPRING_PROFILES_ACTIVE` | none | `dev` enables mock messaging and the simulator; `jetson` is the FERTIMATA Rev A deployment |
| `APP_MODE` | `PRODUCTION` | `DEVELOPMENT` also makes the API docs public |

### Messaging

| Variable | Default | Notes |
|---|---|---|
| `MQTT_ENABLED` | `true` | `false` selects the in-memory loopback transport |
| `MQTT_BROKER_URL` | `tcp://localhost:1883` | |
| `MQTT_USERNAME` / `MQTT_PASSWORD` | empty | Never logged, never exposed through the API |
| `MQTT_TOPIC_*` | see below | Provisional templates |

### Device liveness and ingestion policy

| Variable | Default | Notes |
|---|---|---|
| `DEVICE_OFFLINE_TIMEOUT_SECONDS` | `120` | **Provisional** — depends on the firmware heartbeat rate |
| `DEVICE_LIVENESS_CHECK_INTERVAL_SECONDS` | `30` | |
| `DEVICE_AUTO_REGISTER` | `true` | Create a device row on first contact (`false` under `jetson`) |

### Hardware catalog and command safety

| Variable | Default | Notes |
|---|---|---|
| `HARDWARE_CATALOG_LOCATION` | `classpath:hardware/fertimata-rev-a.yml` | Environment variable only. Devices, sensors, actuators and interlocks of one hardware revision |
| `HARDWARE_CATALOG_SYNC` | `true` | Create missing catalog rows at startup; never modifies existing rows |
| `ACTUATOR_REFUSE_WHEN_DEVICE_OFFLINE` | `false` (`true` under `jetson`) | Refuse activating commands for a device that is not ONLINE |
| `ACTUATOR_COMMAND_TTL` | empty (`30s` under `jetson`, `60s` under `dev`) | Adds `expiresAt` to commands; the edge agent discards late ones |

| `TELEMETRY_AUTO_REGISTER_SENSORS` | `true` | Create a disabled sensor for an unknown metric |
| `TELEMETRY_MAX_CLOCK_SKEW_SECONDS` | `300` | Guards against an unsynced device clock |
| `TELEMETRY_MAX_METRICS_PER_MESSAGE` | `64` | |

### Telemetry retention

Every telemetry message is stored twice: the raw payload in `telemetry_message` (jsonb) and one
`sensor_reading` row per metric. Without a cap that is hundreds of thousands of rows per day, which is
what a scheduled purge now prevents. Rows are deleted in small batches so the table is never locked
for long.

| Variable | Default | Notes |
|---|---|---|
| `TELEMETRY_RETENTION_ENABLED` | `true` | `false` keeps everything forever |
| `TELEMETRY_READINGS_MAX_AGE` | `30d` (`7d` under `dev`, `90d` under `jetson`) | Age of `sensor_reading` rows, by `recorded_at` |
| `TELEMETRY_RAW_MESSAGES_MAX_AGE` | `3d` (`1d` under `dev`, `7d` under `jetson`) | Age of raw `telemetry_message` rows, by `received_at` |
| `TELEMETRY_RETENTION_INTERVAL` | `1h` | How often the purge runs |
| `TELEMETRY_RETENTION_BATCH_SIZE` | `5000` (`2000` under `jetson`) | Rows per delete transaction |
| `TELEMETRY_RETENTION_MAX_BATCHES_PER_RUN` | `40` | Ceiling per run; a large backlog drains over several runs |

Application logs are **not** stored in the database. They go to stdout (Docker's json-file driver,
capped at 10 MB x 3 per container in `deploy/docker-compose.yml`).

The full list, with comments, is in [`.env.example`](.env.example).

---

## Database

PostgreSQL. The schema belongs to **Flyway** (`src/main/resources/db/migration`); Hibernate runs
with `ddl-auto=validate` and never changes it. Every schema change is a new versioned migration.

```
app_user            operator accounts (BCrypt hashes)
device              edge devices, liveness
sensor              measurement channels — a sensor is a ROW, never a table
actuator            controllable outputs
telemetry_message   raw inbound payloads (jsonb), including rejected ones
sensor_reading      one normalised row per metric per message (message link nulled once the raw payload is purged)
actuator_command    append-only audit: who, what, when, manual or automatic, did it work
alert               operational alerts and acknowledgement
plant               minimal; per-plant tracking is not a settled requirement
ai_detection        results from the vision model on the device
automation_rule     storage for phase 2; nothing evaluates it yet
```

There is no pH column, no NPK table and no pump table. Adding an instrument is data, not a
migration — that is the point.

### Supabase

Works unchanged. Point `DATABASE_URL` at the connection pooler and require SSL:

```
DATABASE_URL=jdbc:postgresql://aws-0-<region>.pooler.supabase.com:6543/postgres?sslmode=require
DATABASE_USERNAME=postgres.<project-ref>
DATABASE_PASSWORD=<database-password>
```

Local development does not need Supabase; the `docker compose` PostgreSQL is enough.

---

## MQTT

### Topics — provisional

The naming convention is **not locked with the hardware team**, so no topic string exists in Java
code. Every topic is built from a configurable template; `{deviceId}` is the device code.

| Kind | Template | Direction |
|---|---|---|
| Telemetry | `fertimata/{deviceId}/telemetry` | device → backend |
| Status / heartbeat | `fertimata/{deviceId}/status` | device → backend |
| Command | `fertimata/{deviceId}/command` | backend → device |
| Command ack | `fertimata/{deviceId}/command/ack` | device → backend |
| AI detection | `fertimata/{deviceId}/ai` | device → backend |
| Device alert | `fertimata/{deviceId}/alert` | device → backend |

Change them in `app.mqtt.topics` (or the `MQTT_TOPIC_*` variables) and the whole system follows.

### Payloads — provisional

Telemetry:

```json
{
  "deviceId": "JETSON-001",
  "timestamp": "2026-09-06T12:00:00Z",
  "data": { "temperature": 28.4, "ph": 6.2, "ec": 1.8, "tds": 920 }
}
```

The metric names are **not fixed anywhere in the backend** — they are matched against registered
sensors at runtime. The device code is taken from the **topic**, not from the body, so a device
cannot write telemetry attributed to another device.

Status / heartbeat (every field optional; an empty message still counts as proof of life):

```json
{ "deviceId": "JETSON-001", "timestamp": "...", "status": "ONLINE",
  "actuators": { "ACTUATOR-001": "OFF" } }
```

Command (backend → device):

```json
{ "commandUid": "0b4f…", "deviceId": "JETSON-001", "actuatorCode": "ACTUATOR-001",
  "command": "ON", "parameters": { "durationSeconds": 60 }, "issuedAt": "..." }
```

Command acknowledgement (device → backend) — the only thing that can mark a command `EXECUTED`:

```json
{ "commandUid": "0b4f…", "success": true, "actuatorState": "ON", "executedAt": "..." }
```

AI detection:

```json
{ "deviceId": "JETSON-001", "detectedAt": "...", "detectionType": "DISEASE",
  "label": "<whatever the model reports>", "confidence": 0.93, "imageUrl": "..." }
```

---

## API

Base path `/api`. Every endpoint except login requires `Authorization: Bearer <token>`.
Full documentation: **<http://localhost:8080/swagger-ui.html>**

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/auth/login` | Sign in, receive a JWT |
| `GET` | `/api/auth/me` | Describe the current account |
| `GET` | `/api/devices` | List devices |
| `GET` | `/api/devices/{id}` | One device |
| `GET` | `/api/devices/{id}/status` | Liveness, including the configured timeout |
| `POST` | `/api/devices` | Register a device |
| `GET` | `/api/sensors` | List sensors (`?deviceId=`) |
| `GET` | `/api/sensors/{id}` | One sensor |
| `GET` | `/api/sensors/{id}/latest` | Most recent reading |
| `GET` | `/api/sensors/{id}/history` | Readings (`?from=&to=&page=&size=`, default last 24 h) |
| `POST` | `/api/sensors` | Register a sensor |
| `PATCH` | `/api/sensors/{id}` | Name, enable or annotate a sensor (`metadata`) |
| `GET` | `/api/actuators` | List actuators (`?deviceId=`) |
| `GET` | `/api/actuators/{id}` | One actuator |
| `GET` | `/api/actuators/{id}/status` | Last **reported** state |
| `POST` | `/api/actuators` | Register an actuator |
| `PATCH` | `/api/actuators/{id}` | Rename, enable/disable, change `maxRunSeconds` (0 removes it) |
| `POST` | `/api/actuators/{id}/commands` | **Command an actuator** → `202` |
| `GET` | `/api/actuators/{id}/commands` | Command history |
| `POST` | `/api/auth/refresh` | Exchange a refresh token for a new pair (single-use, rotated) |
| `POST` | `/api/auth/logout` | Revoke this device's refresh token |
| `POST` | `/api/auth/change-password` | Change own password; other sessions are revoked |
| `GET` `POST` | `/api/users` | Accounts (ADMIN): list, create |
| `PATCH` | `/api/users/{id}` | Name, role, enabled (ADMIN; self and last admin protected) |
| `POST` | `/api/users/{id}/reset-password` | Temporary password, must be changed at next sign-in (ADMIN) |
| `GET` | `/api/cultivation/active` | Today's HST, phase in effect (EC target, N:P:K), next phase |
| `GET` `POST` | `/api/cultivation/cycles` | Planting cycles with growth phases |
| `PATCH` | `/api/cultivation/cycles/{id}` | Rename, dates, status (ACTIVE / ENDED) |
| `PUT` | `/api/cultivation/cycles/{id}/phases` | Replace the growth phases |
| `GET` `POST` | `/api/plants` | Treatment plants (`?cycleId=&treatmentCode=&blockCode=&stationCode=`); `/batch` for many |
| `PATCH` | `/api/plants/{id}` | Update a plant |
| `GET` `POST` | `/api/plants/{id}/observations` | Manual observation log (height, leaves, leaf-3 size, notes + soil snapshot) |
| `GET` | `/api/alerts` | List alerts (`?acknowledged=&severity=&deviceId=&sensorId=&actuatorId=`, filters combine) |
| `GET` | `/api/alerts/{id}` | One alert |
| `PATCH` | `/api/alerts/{id}/acknowledge` | Acknowledge |
| `GET` | `/api/ai/detections` | List detections (`?stationCode=&deviceId=&detectionType=`) |
| `GET` | `/api/ai/detections/{id}` | One detection |
| `GET` | `/api/dashboard/overview` | **Everything the dashboard needs, in one request** |

Development-only (`dev` profile; the routes do not exist otherwise):

| Method | Path |
|---|---|
| `POST` | `/api/dev/telemetry` |
| `POST` | `/api/dev/devices/{deviceCode}/heartbeat` |
| `POST` | `/api/dev/devices/{deviceCode}/status` |
| `POST` | `/api/dev/devices/{deviceCode}/ai-detections` |
| `POST` | `/api/dev/devices/{deviceCode}/alerts` |
| `POST` | `/api/dev/devices/{deviceCode}/command-ack` |
| `GET` | `/api/dev/outbox` |

### Errors

One shape, everywhere. Stack traces are never returned.

```json
{
  "timestamp": "2026-09-15T04:41:07Z",
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "Invalid request",
  "path": "/api/actuators/1/commands",
  "details": { "command": "must not be blank" }
}
```

### Commanding an actuator

```bash
curl -X POST http://localhost:8080/api/actuators/1/commands \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"command":"ON","parameters":{"durationSeconds":60}}'
```

`202 Accepted` means the command reached the messaging layer, **not** that hardware acted on it.
Watch the command status: it becomes `EXECUTED` only when the device acknowledges it.

---

## Realtime

STOMP over WebSocket at `ws://localhost:8080/ws`. Use REST for queries, WebSocket for changes —
do not poll.

```ts
import { Client } from '@stomp/stompjs';

const client = new Client({
  brokerURL: 'ws://localhost:8080/ws',
  connectHeaders: { Authorization: `Bearer ${accessToken}` },  // verified on CONNECT
  onConnect: () => {
    client.subscribe('/topic/telemetry', m => console.log(JSON.parse(m.body)));
    client.subscribe('/topic/alerts',    m => console.log(JSON.parse(m.body)));
  },
});
client.activate();
```

| Destination | Event |
|---|---|
| `/topic/telemetry` | `SENSOR_READING_UPDATED` |
| `/topic/devices` | `DEVICE_STATUS_CHANGED` |
| `/topic/actuators` | `ACTUATOR_STATUS_CHANGED` |
| `/topic/actuators/commands` | `ACTUATOR_COMMAND_UPDATED` |
| `/topic/alerts` | `ALERT_CREATED`, `ALERT_UPDATED` (acknowledged) |
| `/topic/ai` | `AI_DETECTION_CREATED` |
| `/topic/events` | all of the above |

Envelope:

```json
{ "event": "SENSOR_READING_UPDATED", "timestamp": "...", "data": { } }
```

---

## Development mode

**This is what lets the frontend be built before hardware integration is finished.**

```bash
SPRING_PROFILES_ACTIVE=dev MQTT_ENABLED=false ./mvnw spring-boot:run
```

With that, the backend starts with no broker, no Jetson and no sensors, and:

- **Hardware catalog** — the FERTIMATA Rev A devices, sensors and actuators are created from
  `hardware/fertimata-rev-a.yml`, the same inventory the Jetson uses.
- **Telemetry simulator** — sample readings for every catalog device and heartbeats every 10 s, so
  the dashboard is alive.
- **Mock transport** — actuator commands are accepted and recorded; `GET /api/dev/outbox` shows
  what would have been published.
- **Injection endpoints** — `/api/dev/**` pushes a specific telemetry message, heartbeat, AI
  detection or command acknowledgement on demand.

Injected and simulated messages take **the same path a real device message would**, so what you see
in development is what you will see from real hardware.

The simulator values are random numbers inside configured ranges. They are placeholders for
rendering and carry no agronomic meaning — nothing in the backend interprets them.

Everything above is confined to the `dev` profile or to a property. None of it exists in a
production build; `DevToolsController` is annotated `@Profile("dev")`, so its routes are not
registered at all otherwise.

### Exercising the full command lifecycle without hardware

```bash
# 1. command an actuator  → status SENT
curl -X POST localhost:8080/api/actuators/1/commands -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"command":"ON"}'

# 2. see what would have gone to the broker (copy the commandUid)
curl localhost:8080/api/dev/outbox -H "Authorization: Bearer $TOKEN"

# 3. acknowledge as if the device had executed it → status EXECUTED
curl -X POST localhost:8080/api/dev/devices/JETSON-001/command-ack \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"commandUid":"<uid>","success":true,"actuatorState":"ON"}'
```

---

## Testing

```bash
./mvnw test                        # unit tests, no Docker needed
./mvnw test -Pintegration-tests    # integration tests, needs Docker
```

**Unit tests** (39) cover the business-critical behaviour:

| Area | Covered |
|---|---|
| Authentication | successful login, invalid credentials, no password in `toString` |
| Device | registration, duplicate rejection, heartbeat updates `lastSeenAt`, offline detection, recovery alert raised once, auto-registration on and off |
| Telemetry | multi-metric ingestion, unmapped metric → partial accept, unknown device → recorded as rejected, empty payload, oversized payload, clock-skew clamping, non-numeric values |
| Actuator commands | valid command published and recorded `SENT`, disabled actuator refused, publish failure → `FAILED` + alert + error, acknowledgement → `EXECUTED`, device-reported failure, unknown and repeated acknowledgements ignored |
| Alerts | raising, duplicate suppression, acknowledgement, double acknowledgement refused, missing alert |
| Dashboard | overview aggregation across every section |
| MQTT topics | building, wildcard subscriptions, parsing, `command` vs `command/ack` disambiguation, alternative conventions, missing template |

**Integration tests** (8) run against a real PostgreSQL started by Testcontainers. They verify the
Flyway migration applies, that **the schema and the JPA entities actually agree**
(`ddl-auto=validate`), the login flow, unauthenticated rejection, validation error shape, the
telemetry → reading → dashboard path, and the full actuator command flow including the 401 for an
unauthenticated command.

---

## Docker

```bash
docker compose up --build            # postgres + mosquitto + backend
docker compose up -d postgres        # just the database
docker compose logs -f backend
```

The image is multi-stage (build with JDK, run on JRE), runs as a non-root user, has a health check
on `/actuator/health`, and sizes the heap from the container memory limit. No secret is baked in —
everything comes from the environment.

The Mosquitto config allows anonymous access. That is fine on a developer machine and **not fine
anywhere else**: a real broker needs credentials and TLS, which the backend reads from
`MQTT_USERNAME`, `MQTT_PASSWORD` and `MQTT_BROKER_URL`.

---

## Project structure

```
src/main/java/com/fertimata/backend/
├── config/         application properties, OpenAPI, JPA auditing, first-operator bootstrap
├── common/         exception hierarchy + global handler, API error and page shapes, JSON helper
├── security/       Spring Security config, JWT issuing and role mapping, login endpoint
├── user/           accounts and roles
├── device/         device registry, liveness monitoring
├── sensor/         sensor registry, reading queries
├── telemetry/      ingestion pipeline, telemetry_message + sensor_reading
├── actuator/       actuators, command service, command audit, safety/ interlock guards
├── alert/          alerts and acknowledgement
├── ai/             AI detections (single label or multi-label scores, camera stations)
├── catalog/        hardware catalog binding and create-only startup sync
├── plant/          minimal plant entity
├── automation/     rule storage + engine interface (no rules implemented)
├── mqtt/           MqttPublisher port, topic resolver, dispatcher, handlers, payloads, mock
├── websocket/      STOMP config, event model, publisher
├── dashboard/      one-request overview
└── dev/            DEVELOPMENT ONLY: simulator, injection endpoints
```

Controllers stay thin, services hold the rules, repositories only persist, and MQTT lives behind an
interface. Entities are never returned from a controller.

---

## Current limitations

Honest list of what this phase does **not** do:

1. **No automation.** `AutomationEngine` is a no-op and no agricultural threshold is hard-coded
   anywhere. Automatically running a pump is a safety decision that waits for final specifications.
2. **No agronomic alerts.** Only infrastructure alerts (device offline, command failed) exist.
3. **WebSocket does not scale past one instance.** The in-memory STOMP broker only reaches clients
   on the same instance; multiple instances need a broker relay.
4. **No downsampling.** Telemetry older than the retention window is deleted, not aggregated. A
   long-term history (seasons, not weeks) needs a downsampled table.
5. **Roles are coarse.** `ADMIN` / `OPERATOR` / `VIEWER` with a fixed hierarchy; no per-feature
   permissions.
7. **No image storage.** `ai_detection.image_url` is a reference; how images are transported is
   undecided.
8. **Commands are accepted for offline devices.** They are recorded and published and the broker
   holds them. Refusing instead is a rule to add deliberately, not to assume.

---

## Open hardware decisions

The backend is deliberately flexible where the team has not decided yet. These are the answers
still needed, and what is in place meanwhile:

| # | Needed from | Decision | Meanwhile |
|---|---|---|---|
| 1 | Hardware | Final sensor set, units, sampling rate | Rev A catalog in `hardware/fertimata-rev-a.yml`; units still to verify on arrival |
| 2 | Hardware | Final actuator set and command names | Rev A catalog; `commandType` free text, `ON` configured as the activating command |
| 3 | Hardware + agronomy | Command parameter rules (e.g. maximum run time) | Configurable interlocks (incl. float LOW and pan-while-moving) + per-actuator `maxRunSeconds`; limits are the provisional §5.4 values until calibration |
| 4 | Hardware | MQTT topic convention | Configurable templates; no topic string in code |
| 5 | Firmware | Heartbeat interval → the offline timeout | Configurable, provisional default 120 s |
| 6 | Hardware | Telemetry payload shape | Raw payload retained; metric keys resolved at runtime |
| 7 | AI | Output contract: labels, boxes, image transport | Label is free text; extra fields kept in metadata |
| 8 | Agronomy + AI | Whether cultivation is tracked per plant | `plant` minimal, `ai_detection.plant_id` nullable |
| 9 | Agronomy | Thresholds and automation rules | Nothing evaluated; engine is a no-op |
| 10 | Operations | Downsampling for long-term history | Time-based retention purge; no aggregation |

When one of these is answered, the change should be configuration or a small migration — not a
rewrite. That was the goal of this phase.

---

## Security notes

- Passwords are BCrypt-hashed and never logged.
- `JWT_SECRET` has no default; the application will not start without it.
- Secrets are never committed. `.env` is gitignored; `.env.example` holds placeholders only.
- MQTT credentials come from the environment and are never returned by any endpoint.
- CORS uses an explicit origin list, never `*`.
- Requests are validated with Jakarta Bean Validation; request DTOs expose only fields a client may
  set.
- Entities are never serialised to a client.
- Stack traces are never returned; unexpected errors log server-side and return a generic message.
- Device codes are restricted to `[A-Za-z0-9._-]`, keeping MQTT wildcards out of generated topics.
