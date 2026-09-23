package com.djmahirnationtv.status.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackages = {
    "com.djmahirnationtv.status.backend.monitor.repository",
})
public class JpaConfig {
}