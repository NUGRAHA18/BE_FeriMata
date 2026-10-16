package com.smartmelon.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** aktifkan fitur timestamp auditing (created/updated) otomatis yang digunakan oleh entity base class.*/
@Configuration
@EnableJpaAuditing
public class JpaConfig {}
