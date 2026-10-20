package com.fertimata.backend.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Satu {@link Clock} untuk semua yang bergantung pada "hari ini", supaya bisa dibekukan di test. */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
