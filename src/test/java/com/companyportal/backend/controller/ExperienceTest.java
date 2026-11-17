/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.YearMonth;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Work experience on a person's own profile: kept by the person, seen by those who see their record, with totals. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "test.context=experience")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExperienceTest {

    @Autowired
    private MockMvc mockMvc;

    private String owner;
    private long dw;
    private int seq;

    @BeforeAll
    void setUp() throws Exception {
        owner = token(changePin(token(login("owner", "Ams@2026")), "Ams@2026", "Own#2026"));
        List<Integer> brandIds = JsonPath.read(body(mockMvc.perform(get("/api/brands").header("Authorization", auth(owner)))),
                "$[?(@.code == 'DW')].id");
        dw = brandIds.get(0);
    }

    private static String job(String company, String start, String end) {
        return """
                {"companyName":"%s","jobTitle":"Developer","employmentType":"FULL_TIME","location":"Kochi","startMonth":"%s","endMonth":%s,
                 "responsibilities":"Built the order system","achievements":"Cut page load by half","tools":"Java, Spring Boot"}
                """.formatted(company, start, end == null ? "null" : "\"" + end + "\"");
    }

    @Test
    void aPersonKeepsTheirOwnExperienceWithTotals() throws Exception {
        String[] me = employee("2026-01-05");
        // January 2020 to December 2022 (36 months), June 2022 to March 2024 (22, overlapping 7), April 2025 on (still there)
        json(post("/api/me/experience"), me[1], job("Alpha Soft", "2020-01-15", "2022-12-31")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.startMonth").value("2020-01-01")).andExpect(jsonPath("$.endMonth").value("2022-12-01"))
                .andExpect(jsonPath("$.months").value(36));
        String beta = body(json(post("/api/me/experience"), me[1], job("Beta Systems", "2022-06-01", "2024-03-01"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.months").value(22)));
        json(post("/api/me/experience"), me[1], job("Gamma Freelance", "2025-04-01", null)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.current").value(true));

        // latest first; before joining: January 2020 to March 2024 once (51) + April to December 2025 (9) = 60
        mockMvc.perform(get("/api/me/experience").header("Authorization", auth(me[1])))
                .andExpect(jsonPath("$.jobs[*].companyName", contains("Gamma Freelance", "Beta Systems", "Alpha Soft")))
                .andExpect(jsonPath("$.monthsBefore").value(60))
                .andExpect(jsonPath("$.monthsHere").value(greaterThanOrEqualTo(10)));

        // the checks
        String future = YearMonth.now().plusMonths(2).atDay(1).toString();
        json(post("/api/me/experience"), me[1], job("Delta", future, null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("The start month cannot be in the future"));
        json(post("/api/me/experience"), me[1], job("Delta", "2021-05-01", "2021-03-01")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("The end month is before the start month"));
        json(post("/api/me/experience"), me[1], job("", "2021-05-01", null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.companyName").value("Give the company's name"));

        // changed and removed; only by the person
        int id = JsonPath.read(beta, "$.id");
        json(put("/api/me/experience/" + id), me[1], job("Beta Systems Pvt Ltd", "2022-06-01", "2024-03-01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.companyName").value("Beta Systems Pvt Ltd"));
        String[] other = employee("2026-01-05");
        json(put("/api/me/experience/" + id), other[1], job("Hacked", "2022-06-01", null)).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/me/experience/" + id).header("Authorization", auth(other[1]))).andExpect(status().isForbidden());

        // the owner sees it on the record; a colleague without the Employees menu does not
        mockMvc.perform(get("/api/employees/" + me[0] + "/experience").header("Authorization", auth(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.jobs.length()").value(3));
        mockMvc.perform(get("/api/employees/" + me[0] + "/experience").header("Authorization", auth(other[1])))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/me/experience/" + id).header("Authorization", auth(me[1]))).andExpect(status().isNoContent());
        // without Beta: January 2020 to December 2022 (36) + April to December 2025 (9)
        mockMvc.perform(get("/api/me/experience").header("Authorization", auth(me[1])))
                .andExpect(jsonPath("$.jobs.length()").value(2)).andExpect(jsonPath("$.monthsBefore").value(45));
    }

    // ---- helpers ----

    /** {id, token} of a new employee of DW with their own PIN. */
    private String[] employee(String joined) throws Exception {
        String userId = "xp.emp" + (++seq);
        String json = """
                {"name":"Experienced %d","userId":"%s","password":"ab@123","role":"EMPLOYEE","brandId":%d,
                 "phone":"+91 98470 00000","joinedDate":"%s"}
                """.formatted(seq, userId, dw, joined);
        String id = String.valueOf((Object) JsonPath.read(body(mockMvc.perform(post("/api/employees").header("Authorization", auth(owner))
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isCreated())), "$.id"));
        return new String[]{id, token(changePin(token(login(userId, "ab@123")), "ab@123", "cd@456"))};
    }

    private ResultActions json(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
        return mockMvc.perform(request.header("Authorization", auth(token)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions login(String userId, String pin) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"%s\",\"password\":\"%s\"}".formatted(userId, pin)));
    }

    private ResultActions changePin(String token, String current, String next) throws Exception {
        return mockMvc.perform(post("/api/auth/change-password").header("Authorization", auth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(current, next)));
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private static String token(ResultActions result) throws Exception {
        return JsonPath.read(body(result), "$.token");
    }

    private static String auth(String token) {
        return "asd " + token;
    }
}
