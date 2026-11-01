package com.fertimata.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.BinarySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.PathParameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Metadata OpenAPI dan skema bearer-token. Jalur non-controller (foto patroli, health) ditulis manual agar
 * import Postman lengkap; WebSocket dijelaskan di deskripsi. */
@Configuration
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI fertiMataOpenApi(AppProperties appProperties) {
        return new OpenAPI()
                .info(new Info()
                        .title("FertiMata Backend API")
                        .version("0.2.0")
                        .description(
                                """
                                Backend for the FertiMata smart-agriculture platform.

                                The backend is the application layer between the operator PWA and the IoT
                                infrastructure. Clients use REST for queries and commands, and the STOMP
                                WebSocket endpoint for realtime updates. Clients never talk to MQTT and never
                                see hardware details.

                                **Realtime (STOMP over WebSocket at `/ws`).** Send the access token in the STOMP
                                CONNECT frame (`Authorization: Bearer <token>`). Every event is
                                `{event, timestamp, data}` and is also published on `/topic/events`:

                                | event | topic | data |
                                |---|---|---|
                                | SENSOR_READING_UPDATED | /topic/telemetry | device + readings of one message |
                                | DEVICE_STATUS_CHANGED | /topic/devices | DeviceStatus |
                                | ACTUATOR_STATUS_CHANGED | /topic/actuators | ActuatorStatus |
                                | ACTUATOR_UPDATED | /topic/actuators | Actuator (settings changed) |
                                | ACTUATOR_COMMAND_UPDATED | /topic/actuators/commands | ActuatorCommand |
                                | SENSOR_UPDATED | /topic/sensors | Sensor (registered, renamed, enabled/disabled) |
                                | ALERT_CREATED, ALERT_UPDATED | /topic/alerts | Alert |
                                | AI_DETECTION_CREATED | /topic/ai | AiDetection |

                                Running in %s mode.
                                """
                                        .formatted(appProperties.mode()))
                        .contact(new Contact().name("FertiMata team"))
                        .license(new License().name("Internal project")))
                .paths(new Paths()
                        .addPathItem("/captures/{date}/{file}", capturesPath())
                        .addPathItem("/actuator/health", healthPath()))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Obtain a token from POST /api/auth/login")));
    }

    /** Seluruh API: /v3/api-docs/semua → openapi/fertimata-openapi.json. */
    @Bean
    public GroupedOpenApi allEndpoints() {
        return GroupedOpenApi.builder().group("semua").displayName("Semua endpoint").pathsToMatch("/**").build();
    }

    /** Subset untuk layanan AI: /v3/api-docs/layanan-ai → openapi/fertimata-openapi-layanan-ai.json (koleksi Postman tim AI),
     * mengikuti alurnya: login → input fuzzy → cari pompa → kirim perintah → tunggu hasil. */
    @Bean
    public GroupedOpenApi aiServiceEndpoints() {
        return GroupedOpenApi.builder()
                .group("layanan-ai")
                .displayName("Layanan AI")
                .pathsToMatch(
                        "/api/auth/login",
                        "/api/auth/refresh",
                        "/api/ai/inputs",
                        "/api/cultivation/active",
                        "/api/actuators",
                        "/api/actuators/*/status",
                        "/api/actuators/*/commands",
                        "/api/actuators/commands/*",
                        "/actuator/health")
                .addOpenApiCustomizer(openApi -> {
                    // Layanan AI membaca foto langsung dari folder di Jetson, dan tidak mendaftarkan aktuator.
                    openApi.getPaths().remove("/captures/{date}/{file}");
                    PathItem actuators = openApi.getPaths().get("/api/actuators");
                    if (actuators != null) {
                        actuators.setPost(null);
                    }
                })
                .addOpenApiCustomizer(openApi -> openApi.getInfo()
                        .title("FertiMata Backend API: layanan AI")
                        .description(
                                """
                                Endpoint yang dibutuhkan layanan AI (penjadwal dosing/campur pupuk di Jetson).
                                Alamat di Jetson: `http://127.0.0.1` (lewat Caddy, bukan port 8080). Login dengan
                                akun OPERATOR khusus layanan (mis. `layanan-ai`, dibuat admin).

                                1. `POST /api/auth/login` → `accessToken` (60 menit); perpanjang dengan `POST /api/auth/refresh`.
                                2. `GET /api/ai/inputs` → nilai sensor dengan nama field persis seperti form fuzzy
                                   (`fertilization`, `irrigation`) + `hst`. Jangan dosing bila `readyForFertilization`
                                   false (data kosong/basi > 15 menit, atau tanggal tanam belum diisi); alasannya di `problems`.
                                3. `GET /api/actuators` → `id` pompa (`DOSING-N/P/K`, `STIRRER`) dan `maxRunSeconds`.
                                4. `POST /api/actuators/{id}/commands` `{"command":"ON","parameters":{"durationSeconds":N}}`
                                   → `commandUid`. Ditolak aturan keselamatan → 409 dengan `details.interlock`.
                                5. `GET /api/actuators/commands/{commandUid}` sampai `EXECUTED` atau `FAILED`
                                   (`failureCode`, mis. NO_RESPONSE, INTERLOCK_POWER); lalu langkah berikutnya.

                                Penyiraman terjadwal dikerjakan backend, bukan layanan AI.
                                """))
                .build();
    }

    /** Foto patroli kamera, disajikan read-only dari folder edge (CAPTURES_DIR). */
    private static PathItem capturesPath() {
        return new PathItem().get(new Operation()
                .tags(List.of("Captures"))
                .summary("Download a patrol photo")
                .description("Read-only file from the edge agent's photo folder (CAPTURES_DIR), e.g. "
                        + "/captures/2026-10-22/20261022-1905_S03_timur.png. Same Bearer token as the API, so a "
                        + "browser must fetch it and show the blob; a plain <img src> gets 401. 404 when "
                        + "CAPTURES_DIR is not set or the file does not exist.")
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .addParametersItem(new PathParameter().name("date").description("Capture day, yyyy-MM-dd")
                        .example("2026-10-22").schema(new StringSchema()))
                .addParametersItem(new PathParameter().name("file").description("File name of the photo")
                        .example("20261022-1905_S03_timur.png").schema(new StringSchema()))
                .responses(new ApiResponses()
                        .addApiResponse("200", new ApiResponse().description("The image")
                                .content(new Content().addMediaType("image/png", new MediaType().schema(new BinarySchema()))))
                        .addApiResponse("401", new ApiResponse().description("Missing or invalid token"))
                        .addApiResponse("404", new ApiResponse().description("No such photo"))));
    }

    /** Health check untuk Docker/Caddy dan pemantauan; tanpa login. */
    private static PathItem healthPath() {
        return new PathItem().get(new Operation()
                .tags(List.of("Health"))
                .summary("Backend health")
                .description("Public. {\"status\":\"UP\"} when the backend and its database are reachable.")
                .responses(new ApiResponses()
                        .addApiResponse("200", new ApiResponse().description("Up")
                                .content(new Content().addMediaType("application/json", new MediaType().schema(new ObjectSchema()))))
                        .addApiResponse("503", new ApiResponse().description("Down"))));
    }
}
