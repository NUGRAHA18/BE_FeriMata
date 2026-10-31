package com.fertimata.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fertimata.backend.telemetry.TelemetryIngestService;
import com.fertimata.backend.telemetry.domain.TelemetrySource;
import com.fertimata.backend.telemetry.domain.TelemetryStatus;
import com.fertimata.backend.telemetry.dto.TelemetryIngestResult;
import com.fertimata.backend.telemetry.history.SensorReadingRollupJob;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * End-to-end against real PostgreSQL (tag {@code integration}, needs Docker: {@code ./mvnw test -Dgroups=integration}).
 * {@code ddl-auto=validate} proves the Flyway migrations and JPA entities agree.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Tag("integration")
class FertiMataIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TelemetryIngestService telemetryIngestService;

    @Autowired
    private SensorReadingRollupJob rollupJob;

    @Autowired
    private com.fertimata.backend.irrigation.IrrigationScheduleRunner irrigationRunner;

    @Test
    @DisplayName("Flyway applies the schema and the JPA entities validate against it")
    void schemaMatchesEntities() {
        Integer applied = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success = true", Integer.class);
        assertThat(applied).isNotNull().isPositive();

        Integer tables = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'public'", Integer.class);
        assertThat(tables).isNotNull().isGreaterThanOrEqualTo(10);
    }

    @Test
    @DisplayName("an operator can sign in and describe itself")
    void loginAndWhoAmI() throws Exception {
        String token = signIn();

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("test-operator"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("wrong credentials return 401 in the standard error shape and no token")
    void wrongCredentials() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "test-operator", "password", "not-the-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("AUTHENTICATION_FAILED"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    @DisplayName("an unauthenticated request is refused with the standard error shape")
    void unauthenticatedRequestIsRefused() throws Exception {
        mockMvc.perform(get("/api/devices"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("AUTHENTICATION_FAILED"))
                .andExpect(jsonPath("$.path").value("/api/devices"));
    }

    @Test
    @DisplayName("an invalid login body returns field-level validation details")
    void validationErrorsAreStructured() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "", "password", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.username").exists())
                .andExpect(jsonPath("$.details.password").exists());
    }

    @Test
    @DisplayName("telemetry injected for a registered sensor becomes a reading and reaches the dashboard")
    void telemetryFlowsThroughToTheDashboard() throws Exception {
        String token = signIn();

        long deviceId = created(mockMvc.perform(post("/api/devices")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "deviceCode", "IT-DEVICE-1",
                                "name", "Integration device",
                                "type", "EDGE_GATEWAY"))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());

        long sensorId = created(mockMvc.perform(post("/api/sensors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "deviceId", deviceId,
                                "code", "IT-SENSOR-1",
                                "metricKey", "temperature",
                                "name", "Integration sensor",
                                "type", "TEMPERATURE",
                                "unit", "C"))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());

        // Through the service, not the dev-only endpoint: the same code path the MQTT handler calls.
        TelemetryIngestResult ingest = telemetryIngestService.ingest(
                "IT-DEVICE-1", Instant.now(), Map.of("temperature", new BigDecimal("28.4")), TelemetrySource.MQTT);
        assertThat(ingest.status()).isEqualTo(TelemetryStatus.ACCEPTED);
        assertThat(ingest.acceptedMetrics()).isEqualTo(1);

        // Sensor nonaktif hilang dari daftar biasa, tapi tetap ada untuk layar Pengaturan.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/sensors/" + sensorId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("enabled", false))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/sensors").param("deviceId", Long.toString(deviceId))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/sensors").param("deviceId", Long.toString(deviceId)).param("includeDisabled", "true")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].enabled").value(false));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/sensors/" + sensorId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("enabled", true))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/sensors/" + sensorId + "/latest")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metricKey").value("temperature"))
                .andExpect(jsonPath("$.value").value(28.4))
                .andExpect(jsonPath("$.unit").value("C"));

        mockMvc.perform(get("/api/dashboard/overview").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.system.mode").value("DEVELOPMENT"))
                .andExpect(jsonPath("$.devices.total").isNumber())
                .andExpect(jsonPath("$.sensors.latestReadings[?(@.metricKey=='temperature')]").exists());
    }

    @Test
    @DisplayName("an actuator command is refused without a token and recorded as SENT with one")
    void actuatorCommandRequiresAuthentication() throws Exception {
        String token = signIn();

        long deviceId = created(mockMvc.perform(post("/api/devices")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("deviceCode", "IT-DEVICE-2", "name", "Command device"))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());

        long actuatorId = created(mockMvc.perform(post("/api/actuators")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "deviceId", deviceId,
                                "code", "IT-ACTUATOR-1",
                                "name", "Integration actuator",
                                "type", "OUTPUT"))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());

        String body = json(Map.of("command", "ON", "parameters", Map.of("durationSeconds", 30)));

        mockMvc.perform(post("/api/actuators/" + actuatorId + "/commands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        String issued = mockMvc.perform(post("/api/actuators/" + actuatorId + "/commands")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.requestedBy").value("test-operator"))
                .andExpect(jsonPath("$.commandUid").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String commandUid = objectMapper.readTree(issued).get("commandUid").asString();

        // Layanan AI menunggu tiap langkah lewat commandUid.
        mockMvc.perform(get("/api/actuators/commands/" + commandUid).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commandUid").value(commandUid))
                .andExpect(jsonPath("$.status").value("SENT"));
        mockMvc.perform(get("/api/actuators/commands/does-not-exist").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/actuators/" + actuatorId + "/commands")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].commandType").value("ON"))
                .andExpect(jsonPath("$.content[0].parameters.durationSeconds").value(30));
    }

    @Test
    @DisplayName("a command for an actuator that does not exist is a 404, not a 500")
    void commandForMissingActuator() throws Exception {
        String token = signIn();

        mockMvc.perform(post("/api/actuators/999999/commands")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("command", "ON"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("hourly rollups keep long-range history after raw readings are purged")
    void aggregatedHistorySurvivesRawPurge() throws Exception {
        String token = signIn();

        long deviceId = created(mockMvc.perform(post("/api/devices")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("deviceCode", "IT-DEVICE-3", "name", "History device"))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());

        long sensorId = created(mockMvc.perform(post("/api/sensors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "deviceId", deviceId,
                                "code", "IT-SENSOR-HUM",
                                "metricKey", "humidity",
                                "name", "History sensor",
                                "unit", "%"))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());

        Instant now = Instant.now();
        Instant oldHour = now.minus(Duration.ofDays(3)).truncatedTo(ChronoUnit.HOURS);
        ingestHumidity(oldHour.plus(Duration.ofMinutes(10)), "10");
        ingestHumidity(oldHour.plus(Duration.ofMinutes(20)), "20");
        ingestHumidity(now.minus(Duration.ofMinutes(1)), "30");
        rollupJob.runOnce(Instant.now());

        // Kiriman terlambat dari buffer edge: recorded_at lama, diterima sekarang. Jamnya dihitung ulang.
        ingestHumidity(oldHour.plus(Duration.ofMinutes(30)), "60");
        rollupJob.runOnce(Instant.now());
        rollupJob.runOnce(Instant.now());

        Map<String, Object> hour = jdbcTemplate.queryForMap(
                "select sample_count, value_sum, value_min, value_max from sensor_reading_hourly"
                        + " where sensor_id = ? and bucket_start = ?",
                sensorId,
                java.sql.Timestamp.from(oldHour));
        assertThat(hour.get("sample_count")).isEqualTo(3);
        assertThat((BigDecimal) hour.get("value_sum")).isEqualByComparingTo("90");
        assertThat((BigDecimal) hour.get("value_min")).isEqualByComparingTo("10");
        assertThat((BigDecimal) hour.get("value_max")).isEqualByComparingTo("60");

        // Seperti retensi: pembacaan mentah lebih tua dari sehari hilang.
        jdbcTemplate.update(
                "delete from sensor_reading where sensor_id = ? and recorded_at < ?",
                sensorId,
                java.sql.Timestamp.from(now.minus(Duration.ofDays(1))));

        String body = mockMvc.perform(get("/api/sensors/" + sensorId + "/history/aggregate")
                        .param("from", now.minus(Duration.ofDays(4)).toString())
                        .param("interval", "1h")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interval").value("1h"))
                .andExpect(jsonPath("$.unit").value("%"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode points = objectMapper.readTree(body).get("points");
        assertThat(points).hasSize(2);
        assertThat(points.get(0).get("bucketStart").asString()).isEqualTo(oldHour.toString());
        assertThat(points.get(0).get("count").asLong()).isEqualTo(3);
        assertThat(points.get(0).get("avg").decimalValue()).isEqualByComparingTo("30");
        assertThat(points.get(0).get("min").decimalValue()).isEqualByComparingTo("10");
        assertThat(points.get(0).get("max").decimalValue()).isEqualByComparingTo("60");
        // Jam terakhir dari data mentah saja, tidak dihitung dua kali bersama ringkasannya.
        assertThat(points.get(1).get("count").asLong()).isEqualTo(1);
        assertThat(points.get(1).get("avg").decimalValue()).isEqualByComparingTo("30");

        // Titik harian menggabungkan ringkasan dan data mentah dengan rata-rata berbobot jumlah sampel.
        mockMvc.perform(get("/api/sensors/" + sensorId + "/history/aggregate")
                        .param("from", now.minus(Duration.ofDays(4)).toString())
                        .param("interval", "1d")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points[*].count").value(org.hamcrest.Matchers.hasItem(3)));
    }

    @Test
    @DisplayName("without VAPID keys push reports itself disabled and refuses subscriptions")
    void pushWithoutKeysIsDisabled() throws Exception {
        String token = signIn();

        mockMvc.perform(get("/api/push/config").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.subscriptions").value(0));

        mockMvc.perform(post("/api/push/subscriptions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "endpoint", "https://fcm.googleapis.com/fcm/send/it",
                                "keys", Map.of("p256dh", "x", "auth", "y")))))
                .andExpect(status().isConflict());

        Integer table = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'push_subscription'", Integer.class);
        assertThat(table).isEqualTo(1);
    }

    @Test
    @DisplayName("a plant is added by name, listed, and deleted; removed research endpoints are gone")
    void plantLifecycle() throws Exception {
        String token = signIn();
        String body = mockMvc.perform(post("/api/plants")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Melon uji 01", "description", "dekat pintu"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("Melon-uji-01"))
                .andExpect(jsonPath("$.treatmentCode").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long plantId = created(body);

        mockMvc.perform(get("/api/plants").param("active", "true").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + plantId + ")].name").value(org.hamcrest.Matchers.hasItem("Melon uji 01")));
        mockMvc.perform(post("/api/plants/" + plantId + "/observations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("heightCm", 40))))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(get("/api/cultivation/cycles").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/plants/" + plantId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/plants/" + plantId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());

        Integer droppedTables = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_name in ('plant_observation', 'harvest_record', 'automation_rule')",
                Integer.class);
        assertThat(droppedTables).isZero();
    }

    @Test
    @DisplayName("the sowing date drives HST and the AI fuzzy phases")
    void sowingDateUsesAiPhases() throws Exception {
        String token = signIn();
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Jakarta"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/cultivation/sowing-date")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("sowingDate", today.minusDays(30).toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hst").value(30))
                .andExpect(jsonPath("$.currentPhase.name").value("Pembungaan"))
                .andExpect(jsonPath("$.currentPhase.ecTargetMin").value(1.2))
                .andExpect(jsonPath("$.nextPhase.name").value("Pembuahan"))
                .andExpect(jsonPath("$.daysUntilNextPhase").value(5))
                .andExpect(jsonPath("$.cycle.sowingDate").value(today.minusDays(30).toString()));

        // Frontend lama: alamat dan nama field lama tetap diterima.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/cultivation/transplant-date")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("transplantDate", today.minusDays(10).toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hst").value(10))
                .andExpect(jsonPath("$.cycle.transplantDate").value(today.minusDays(10).toString()));
    }

    @Test
    @DisplayName("the AI inputs snapshot uses the AI form field names and marks missing data")
    void aiInputs() throws Exception {
        String token = signIn();

        mockMvc.perform(get("/api/ai/inputs").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fertilization").exists())
                .andExpect(jsonPath("$.fertilization.ec").hasJsonPath())
                .andExpect(jsonPath("$.irrigation.suhu_udara").hasJsonPath())
                .andExpect(jsonPath("$.maxAgeSeconds").value(900))
                .andExpect(jsonPath("$.problems").isArray());
    }

    @Test
    @DisplayName("a daily watering time starts the irrigation pump once, through the normal command path")
    void scheduledIrrigation() throws Exception {
        String token = signIn();
        long deviceId = created(mockMvc.perform(post("/api/devices")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("deviceCode", "IT-PANEL", "name", "Pump panel"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/actuators")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("deviceId", deviceId, "code", "IT-PUMP", "name", "Pompa siram",
                                "type", "OUTPUT", "maxRunSeconds", 900))))
                .andExpect(status().isCreated());

        java.time.ZoneId wib = java.time.ZoneId.of("Asia/Jakarta");
        java.time.ZonedDateTime soon = java.time.ZonedDateTime.now(wib).plusMinutes(2).withSecond(0).withNano(0);
        String time = "%02d:%02d".formatted(soon.getHour(), soon.getMinute());
        String body = mockMvc.perform(post("/api/irrigation/schedules")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("time", time, "durationSeconds", 240))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.time").value(time))
                .andExpect(jsonPath("$.nextRunAt").value(soon.toInstant().toString()))
                .andReturn().getResponse().getContentAsString();
        long scheduleId = created(body);

        mockMvc.perform(post("/api/irrigation/schedules")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("time", time, "durationSeconds", 60))))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/irrigation/schedules")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("time", "23:59", "durationSeconds", 901))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details.reason").value("TOO_LONG"));

        // Satu menit setelah jamnya: dijalankan; pemeriksaan berikutnya tidak menyiram lagi.
        irrigationRunner.runDue(soon.plusMinutes(1).toInstant());
        irrigationRunner.runDue(soon.plusMinutes(2).toInstant());

        Map<String, Object> schedule = jdbcTemplate.queryForMap(
                "select last_result, last_command_uid from irrigation_schedule where id = ?", scheduleId);
        assertThat(schedule.get("last_result")).isEqualTo("STARTED");
        Integer commands = jdbcTemplate.queryForObject(
                "select count(*) from actuator_command c join actuator a on a.id = c.actuator_id"
                        + " where a.code = 'IT-PUMP' and c.source = 'SYSTEM' and c.parameters->>'durationSeconds' = '240'",
                Integer.class);
        assertThat(commands).isEqualTo(1);
        mockMvc.perform(get("/api/actuators/commands/" + schedule.get("last_command_uid"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commandType").value("ON"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/irrigation/schedules/" + scheduleId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    private void ingestHumidity(Instant recordedAt, String value) {
        TelemetryIngestResult result = telemetryIngestService.ingest(
                "IT-DEVICE-3", recordedAt, Map.of("humidity", new BigDecimal(value)), TelemetrySource.MQTT);
        assertThat(result.status()).isEqualTo(TelemetryStatus.ACCEPTED);
    }

    private String signIn() throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "test-operator", "password", "test-operator-password"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asString();
    }

    private long created(String responseBody) {
        JsonNode node = objectMapper.readTree(responseBody);
        return node.get("id").asLong();
    }

    private String json(Object value) {
        return objectMapper.writeValueAsString(value);
    }
}
