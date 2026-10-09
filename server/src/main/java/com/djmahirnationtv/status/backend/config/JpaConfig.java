package com.djmahirnationtv.status.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackages = {
    "com.djmahirnationtv.status.backend.monitor.repository",
    "com.djmahirnationtv.status.backend.auth",
    "com.djmahirnationtv.status.backend.integration",
    "com.djmahirnationtv.status.backend.statuspage",
    "com.djmahirnationtv.status.backend.incident",
    "com.djmahirnationtv.status.backend.team",
})
public class JpaConfig {
}
