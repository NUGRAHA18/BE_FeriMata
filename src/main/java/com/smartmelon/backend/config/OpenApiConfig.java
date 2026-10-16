package com.smartmelon.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Metadata dokumen OpenAPI serta skema keamanan bearer-token yang digunakan di seluruh API.*/
@Configuration
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI smartMelonOpenApi(AppProperties appProperties) {
        return new OpenAPI()
                .info(new Info()
                        .title("Smart Melon Backend API")
                        .version("0.1.0")
                        .description(
                                """
                                Backend for the Smart Melon smart-agriculture platform.

                                The backend is the application layer between the operator PWA and the IoT
                                infrastructure. Clients use REST for queries and commands, and the STOMP
                                WebSocket endpoint for realtime updates. Clients never talk to MQTT and never
                                see hardware details.

                                Hardware specifications are not final: sensor kinds, actuator kinds, command
                                names, MQTT topics and automation thresholds are configuration or data, not
                                fixed parts of this contract.

                                Running in %s mode.
                                """
                                        .formatted(appProperties.mode()))
                        .contact(new Contact().name("Smart Melon team"))
                        .license(new License().name("Internal project")))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Obtain a token from POST /api/auth/login")));
    }
}
