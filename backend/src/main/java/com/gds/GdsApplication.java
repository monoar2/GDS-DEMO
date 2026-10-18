package com.gds;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.core.Ordered;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
// Caching wraps the transactional proxy, so a cache hit never borrows a database connection.
@EnableCaching(order = Ordered.HIGHEST_PRECEDENCE)
@EnableScheduling
@ConfigurationPropertiesScan
public class GdsApplication {

    public static void main(String[] args) {
        SpringApplication.run(GdsApplication.class, args);
    }
}
