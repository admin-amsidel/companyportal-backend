/*
 * Copyright (c) 2026 AMSIDEL Technologies. All rights reserved.
 * Designed and developed by AMSIDEL Technologies.
 */
package com.companyportal.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The web addresses the browser may call the API from: example.com, any of its sub-domains, and the listed others. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"test.context=cors",
        "app.cors.allowed-origins=https://example.com, https://*.example.com,https://companyportal-web.example.workers.dev,http://localhost:4200"})
class CorsTest {

    @Autowired
    private MockMvc mockMvc;

    private ResultActions preflight(String origin) throws Exception {
        return mockMvc.perform(options("/api/auth/login").header("Origin", origin).header("Access-Control-Request-Method", "POST"));
    }

    @Test
    void exampleDomainAndEverySubDomainAreAllowed() throws Exception {
        for (String origin : new String[]{"https://example.com", "https://www.example.com", "https://portal.example.com", "https://staff.example.com",
                "https://companyportal-web.example.workers.dev", "http://localhost:4200"}) {
            preflight(origin).andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", origin));
        }
    }

    @Test
    void othersAreRefused() throws Exception {
        for (String origin : new String[]{"https://evil.com", "https://example.com.evil.com", "https://notexample.com", "http://www.example.com", "http://localhost:4300"}) {
            preflight(origin).andExpect(status().isForbidden()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        }
    }
}
