package com.djmahirnationtv.status.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableMongoRepositories(basePackages = {
    "com.djmahirnationtv.status.backend.ping.repository",
})
public class MongoConfig {
}
