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
import java.math.BigDecimal;
import java.time.Instant;
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
 * End-to-end checks against a real PostgreSQL instance.
 *
 * <p>Tagged {@code integration} and excluded from the default build, because it needs Docker. Run it
 * with {@code ./mvnw test -Dgroups=integration}.
 *
 * <p>What it is really for: the Flyway migration and the JPA entities are two descriptions of the
 * same schema, and only a real database can prove they agree. {@code ddl-auto=validate} in the test
 * profile turns any drift between them into a failure here rather than a surprise in production.
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

        // Ingestion is driven through the service rather than the development endpoint, because
        // that endpoint only exists under the dev profile. This is the same code path the MQTT
        // handler calls.
        TelemetryIngestResult ingest = telemetryIngestService.ingest(
                "IT-DEVICE-1", Instant.now(), Map.of("temperature", new BigDecimal("28.4")), TelemetrySource.MQTT);
        assertThat(ingest.status()).isEqualTo(TelemetryStatus.ACCEPTED);
        assertThat(ingest.acceptedMetrics()).isEqualTo(1);

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

        mockMvc.perform(post("/api/actuators/" + actuatorId + "/commands")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.requestedBy").value("test-operator"))
                .andExpect(jsonPath("$.commandUid").isNotEmpty());

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
