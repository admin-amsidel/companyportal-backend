/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.tools;

import com.jayway.jsonpath.JsonPath;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole backend on a real PostgreSQL with its tables in the portal schema, as in production: Flyway makes the
 * schema, Hibernate checks and uses it, hand-written SQL finds its tables through the search path, and nothing goes
 * into public.
 */
@SpringBootTest(properties = {
        "test.context=portalschema",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.db.schema=portal",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.datasource.hikari.connection-init-sql=SET lock_timeout = '5s'"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PortalSchemaStartupTest {

    private static final EmbeddedPostgres PG = start();

    private static EmbeddedPostgres start() {
        try {
            return EmbeddedPostgres.builder().start();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> PG.getJdbcUrl("postgres", "postgres"));
        r.add("spring.datasource.username", () -> "postgres");
        r.add("spring.datasource.password", () -> "");
    }

    @AfterAll
    static void stop() throws IOException {
        PG.close();
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void theAppRunsInThePortalSchema() throws Exception {
        String login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"owner\",\"password\":\"Ams@2026\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(login, "$.token");
        String pin = mockMvc.perform(post("/api/auth/change-password").header("Authorization", "asd " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\"Ams@2026\",\"newPassword\":\"Own#2026\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String owner = "asd " + JsonPath.read(pin, "$.token");

        // JPA pages, and pages with hand-written SQL (the storage tables list, the reporting-line query)
        mockMvc.perform(get("/api/employees").header("Authorization", owner)).andExpect(status().isOk());
        mockMvc.perform(get("/api/storage/summary").header("Authorization", owner)).andExpect(status().isOk());
        mockMvc.perform(get("/api/people/org-chart").header("Authorization", owner)).andExpect(status().is2xxSuccessful());
        mockMvc.perform(get("/api/people/me/line").header("Authorization", owner)).andExpect(status().isOk());
        // an admin under the owner: their own reporting line (the recursive query) and bank details
        String ownerId = String.valueOf((Object) JsonPath.read(mockMvc.perform(get("/api/auth/me").header("Authorization", owner))
                .andReturn().getResponse().getContentAsString(), "$.id"));
        String brandId = String.valueOf((Object) JsonPath.read(mockMvc.perform(get("/api/brands").header("Authorization", owner))
                .andReturn().getResponse().getContentAsString(), "$[0].id"));
        mockMvc.perform(post("/api/employees").header("Authorization", owner).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"Admin Two","userId":"pg.admin","password":"ab@123","role":"ADMIN","brandId":%s,"reportsToId":%s,
                         "phone":"+91 98470 00000","joinedDate":"2026-01-05"}""".formatted(brandId, ownerId)))
                .andExpect(status().isCreated());
        String first = JsonPath.read(mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"pg.admin\",\"password\":\"ab@123\"}")).andReturn().getResponse().getContentAsString(), "$.token");
        String admin = "asd " + JsonPath.read(mockMvc.perform(post("/api/auth/change-password").header("Authorization", "asd " + first)
                .contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\"ab@123\",\"newPassword\":\"cd@456\"}"))
                .andReturn().getResponse().getContentAsString(), "$.token");
        mockMvc.perform(get("/api/people/me/line").header("Authorization", admin)).andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/me/bank-details").header("Authorization", admin)).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select current_schema()", String.class)).isEqualTo("portal");
        assertThat(jdbc.queryForObject("select count(*) from pg_tables where schemaname = 'portal' and tablename in ('employees', 'flyway_schema_history')", Integer.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from portal.employees", Integer.class)).isPositive();
        assertThat(jdbc.queryForObject("select count(*) from pg_tables where schemaname = 'public'", Integer.class)).isZero();
    }
}
