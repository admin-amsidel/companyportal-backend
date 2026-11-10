/*
 * Copyright (c) 2026 AMSIDEL Technologies. All rights reserved.
 * Designed and developed by AMSIDEL Technologies.
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

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Administration > Website enquiries: owners and admins see the website's call-back and demo requests and follow them
 * up (status, notes); nobody else does unless given the menu's own tick.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"test.context=enquiryadmin", "app.session.check-initial-delay=PT24H"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EnquiryAdminTest {

    private static final String API = "/api/enquiries";
    private static final String TICK = "9030";

    @Autowired
    private MockMvc mockMvc;

    private String owner;
    private int dw;
    private int seq;
    private long enquiry;

    @BeforeAll
    void setUp() throws Exception {
        owner = token(changePin(token(login("owner", "Ams@2026")), "Ams@2026", "Own#2026"));
        List<Integer> ids = JsonPath.read(body(mockMvc.perform(get("/api/brands").header("Authorization", auth(owner)))), "$[?(@.code == 'DW')].id");
        dw = ids.getFirst();
        Number id = JsonPath.read(body(mockMvc.perform(post("/api/public/enquiries").contentType(MediaType.APPLICATION_JSON).content("""
                {"kind":"CALLBACK","name":"Asha Menon","phone":"+91 98470 12345","email":"asha@example.com","message":"Pricing"}
                """)).andExpect(status().isCreated())), "$.id");
        enquiry = id.longValue();
    }

    /** A new person with this role, and these ticks of their own (none: the role's menus). */
    private String person(String role, String... checkpoints) throws Exception {
        String userId = "eq." + (++seq);
        String id = String.valueOf((Object) JsonPath.read(body(json(post("/api/employees"), owner, """
                {"name":"Person %d","userId":"%s","password":"ab@123","role":"%s","brandId":%d,"phone":"9847000000","joinedDate":"2026-01-05"}
                """.formatted(seq, userId, role, dw)).andExpect(status().isCreated())), "$.id"));
        if (checkpoints.length > 0) {
            json(put("/api/menu-access/EMPLOYEE/" + id), owner, "{\"checkpoints\":[\"" + String.join("\",\"", checkpoints) + "\"]}").andExpect(status().isOk());
        }
        return token(changePin(token(login(userId, "ab@123")), "ab@123", "cd@456"));
    }

    @Test
    void ownerSeesAndFollowsUpEnquiries() throws Exception {
        mockMvc.perform(get(API).header("Authorization", auth(owner))).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + enquiry + ")].name", contains("Asha Menon")))
                .andExpect(jsonPath("$[?(@.id == " + enquiry + ")].status", contains("NEW")))
                .andExpect(jsonPath("$[?(@.id == " + enquiry + ")].message", contains("Pricing")));

        json(put(API + "/" + enquiry), owner, "{\"status\":\"CONTACTED\",\"notes\":\"  Called, wants a quote for 40 staff  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONTACTED"))
                .andExpect(jsonPath("$.notes").value("Called, wants a quote for 40 staff"))
                .andExpect(jsonPath("$.updatedByName").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").exists());
        json(put(API + "/" + enquiry), owner, "{\"status\":\"MAYBE\"}").andExpect(status().isBadRequest());
        json(put(API + "/" + enquiry), owner, "{\"notes\":\"no status\"}").andExpect(status().isBadRequest());
        json(put(API + "/999999"), owner, "{\"status\":\"WON\"}").andExpect(status().isNotFound());
    }

    @Test
    void adminsHaveItEmployeesDoNotUnlessGivenTheTick() throws Exception {
        mockMvc.perform(get(API).header("Authorization", auth(person("ADMIN")))).andExpect(status().isOk());

        String employee = person("EMPLOYEE");
        mockMvc.perform(get(API).header("Authorization", auth(employee))).andExpect(status().isForbidden());
        json(put(API + "/" + enquiry), employee, "{\"status\":\"WON\"}").andExpect(status().isForbidden());
        mockMvc.perform(delete(API + "/" + enquiry).header("Authorization", auth(employee))).andExpect(status().isForbidden());

        mockMvc.perform(get(API).header("Authorization", auth(person("EMPLOYEE", TICK)))).andExpect(status().isOk());
        // signed out: nothing
        mockMvc.perform(get(API)).andExpect(status().isUnauthorized());
    }

    @Test
    void spamCanBeDeleted() throws Exception {
        Number id = JsonPath.read(body(mockMvc.perform(post("/api/public/enquiries").header("X-Forwarded-For", "10.9.9.9")
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"kind":"CALLBACK","name":"Spam Bot","phone":"1234567","email":"spam@example.com"}
                        """)).andExpect(status().isCreated())), "$.id");
        mockMvc.perform(delete(API + "/" + id).header("Authorization", auth(owner))).andExpect(status().isNoContent());
        mockMvc.perform(get(API).header("Authorization", auth(owner))).andExpect(jsonPath("$[*].id", not(hasItem(id.intValue()))));
        mockMvc.perform(delete(API + "/" + id).header("Authorization", auth(owner))).andExpect(status().isNotFound());
    }

    private ResultActions json(MockHttpServletRequestBuilder request, String token, String content) throws Exception {
        return mockMvc.perform(request.header("Authorization", auth(token)).contentType(MediaType.APPLICATION_JSON).content(content));
    }

    private ResultActions login(String userId, String pin) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"%s\",\"password\":\"%s\"}".formatted(userId, pin)));
    }

    private ResultActions changePin(String token, String current, String next) throws Exception {
        return mockMvc.perform(post("/api/auth/change-password").header("Authorization", auth(token)).contentType(MediaType.APPLICATION_JSON)
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
