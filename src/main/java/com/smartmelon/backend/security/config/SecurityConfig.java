package com.smartmelon.backend.security.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.smartmelon.backend.config.AppProperties;
import com.smartmelon.backend.security.jwt.JwtRoleConverter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] DOCS_PATHS = {
        "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**"
    };

    private final JwtProperties jwtProperties;
    private final CorsProperties corsProperties;
    private final AppProperties appProperties;

    public SecurityConfig(JwtProperties jwtProperties, CorsProperties corsProperties, AppProperties appProperties) {
        this.jwtProperties = jwtProperties;
        this.corsProperties = corsProperties;
        this.appProperties = appProperties;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtDecoder jwtDecoder,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler)
            throws Exception {

        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> {
                    requests.requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll();
                    requests.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
                            .permitAll();
                 // Handshake STOMP bersifat publik; frame CONNECT itu sendiri diotentikasi oleh
                 // WebSocketAuthenticationInterceptor, yang dapat membaca token dari frame tersebut.
                    requests.requestMatchers("/ws", "/ws/**").permitAll();
                    if (appProperties.isDevelopment()) {
                        requests.requestMatchers(DOCS_PATHS).permitAll();
                    }
                    requests.anyRequest().authenticated();
                })
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(jwtDecoder)
                .jwtAuthenticationConverter(jwtAuthenticationConverter()))
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(handling -> handling.authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler));

        return http.build();
    }

   /**
     * BCrypt dengan faktor beban (<i>cost factor</i>) bawaan dari pustaka. Dipilih dibandingkan 
     * <i>delegating encoder</i> karena tidak ada format kata sandi terwariskan (<i>legacy</i>) yang perlu 
     * dimigrasi, serta penggunaan format tunggal menjaga konsistensi nilai <i>hash</i> yang tersimpan.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            UserDetailsService userDetailsService, 
            PasswordEncoder passwordEncoder
        ) {
            DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
            provider.setPasswordEncoder(passwordEncoder);
            // Keeps the failure indistinguishable between "no such account" and "wrong password".
            // provider.setHideUserNotFoundExceptions(true);
            return new ProviderManager(provider);
        }

    @Bean
    public SecretKey jwtSigningKey() {
        return new SecretKeySpec(jwtProperties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSigningKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(), new JwtIssuerValidator(jwtProperties.issuer()));
        decoder.setJwtValidator(validator);
        return decoder;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new JwtRoleConverter());
        return converter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(orDefault(corsProperties.allowedOrigins(), List.of()));
        configuration.setAllowedMethods(
                orDefault(corsProperties.allowedMethods(), List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS")));
        configuration.setAllowedHeaders(orDefault(corsProperties.allowedHeaders(), List.of("*")));
        configuration.setAllowCredentials(corsProperties.allowCredentials());
        if (corsProperties.maxAge() != null) {
            configuration.setMaxAge(corsProperties.maxAge());
        }

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        source.registerCorsConfiguration("/ws/**", configuration);
        return source;
    }

    private static List<String> orDefault(List<String> value, List<String> fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }
}
