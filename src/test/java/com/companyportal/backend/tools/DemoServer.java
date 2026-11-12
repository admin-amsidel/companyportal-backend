/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * The whole backend on http://localhost:8080 with a throw-away in-memory database (the test profile: no live database,
 * no mail), kept running for a while, e.g. to fill it with sample data through the API and take screenshots of the
 * portal for a user guide. Never runs in a normal build.
 * <pre>./mvnw test -Dtest=DemoServer -Ddemo.minutes=20</pre>
 * Sign in as owner / Ams@2026 (asked to change it at once). The web app run on http://localhost:4200 talks to it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
        "server.port=8080", "test.context=demo", "app.cors.allowed-origins=http://localhost:4200",
        "app.login.max-failed-attempts=50"})
@ActiveProfiles("test")
class DemoServer {

    @Test
    @EnabledIfSystemProperty(named = "demo.minutes", matches = "\\d+")
    void run() throws InterruptedException {
        long minutes = Long.parseLong(System.getProperty("demo.minutes"));
        System.out.println("DEMO SERVER READY on http://localhost:8080 for " + minutes + " minutes");
        Thread.sleep(minutes * 60_000);
    }
}
