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

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Skills on a person's own profile: kept by the person, seen by those who see their record. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "test.context=skills")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SkillTest {

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

    @Test
    void aPersonKeepsTheirOwnSkills() throws Exception {
        String[] me = employee();
        // added with a note, in the order added; spaces tidied
        json(post("/api/me/skills"), me[1], "{\"name\":\"  Java \",\"note\":\"6 years, Java 17 and 21\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("Java"));
        String boot = body(json(post("/api/me/skills"), me[1], "{\"name\":\"Spring   Boot\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.note").doesNotExist()));
        mockMvc.perform(get("/api/me/skills").header("Authorization", auth(me[1])))
                .andExpect(jsonPath("$[*].name", contains("Java", "Spring Boot")))
                .andExpect(jsonPath("$[0].note").value("6 years, Java 17 and 21"));
        // the same skill twice, in any case, is refused; a name is needed
        json(post("/api/me/skills"), me[1], "{\"name\":\"JAVA\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("You have already listed Java"));
        json(post("/api/me/skills"), me[1], "{\"name\":\" \"}").andExpect(status().isBadRequest());

        // changed and removed
        int id = JsonPath.read(boot, "$.id");
        json(put("/api/me/skills/" + id), me[1], "{\"name\":\"Spring Boot\",\"note\":\"Microservices, REST APIs\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.note").value("Microservices, REST APIs"));
        json(put("/api/me/skills/" + id), me[1], "{\"name\":\"java\"}").andExpect(status().isConflict());

        // only the person changes them; the owner sees them on the record, a colleague without the Employees menu does not
        String[] other = employee();
        json(put("/api/me/skills/" + id), other[1], "{\"name\":\"Hacked\"}").andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/me/skills/" + id).header("Authorization", auth(other[1]))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/employees/" + me[0] + "/skills").header("Authorization", auth(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].name", contains("Java", "Spring Boot")));
        mockMvc.perform(get("/api/employees/" + me[0] + "/skills").header("Authorization", auth(other[1])))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/me/skills/" + id).header("Authorization", auth(me[1]))).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/me/skills").header("Authorization", auth(me[1]))).andExpect(jsonPath("$[*].name", contains("Java")));
    }

    @Test
    void suggestionsAreTheCompanysOwnSkillsThenCommonOnes() throws Exception {
        String[] a = employee();
        json(post("/api/me/skills"), a[1], "{\"name\":\"uPVC welding\"}").andExpect(status().isCreated());
        mockMvc.perform(get("/api/skills/suggestions").param("q", "upvc").header("Authorization", auth(a[1])))
                .andExpect(jsonPath("$[0]").value("uPVC welding")).andExpect(jsonPath("$", hasItem("uPVC fabrication")));
        mockMvc.perform(get("/api/skills/suggestions").param("q", "spring").header("Authorization", auth(a[1])))
                .andExpect(jsonPath("$", hasItem("Spring Boot")));
        mockMvc.perform(get("/api/skills/suggestions").header("Authorization", auth(a[1])))
                .andExpect(jsonPath("$.length()").value(20));
    }

    // ---- helpers ----

    /** {id, token} of a new employee of DW with their own PIN. */
    private String[] employee() throws Exception {
        String userId = "sk.emp" + (++seq);
        String json = """
                {"name":"Skilled %d","userId":"%s","password":"ab@123","role":"EMPLOYEE","brandId":%d,
                 "phone":"+91 98470 00000","joinedDate":"2026-01-05"}
                """.formatted(seq, userId, dw);
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
