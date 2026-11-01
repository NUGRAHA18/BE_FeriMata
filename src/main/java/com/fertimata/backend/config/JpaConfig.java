package com.fertimata.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Mengaktifkan auditing timestamp created/updated untuk entity base class. */
@Configuration
@EnableJpaAuditing
public class JpaConfig {}
