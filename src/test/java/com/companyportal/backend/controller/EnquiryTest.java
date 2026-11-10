/*
 * Copyright (c) 2026 AMSIDEL Technologies. All rights reserved.
 * Designed and developed by AMSIDEL Technologies.
 */
package com.companyportal.backend.controller;

import com.companyportal.backend.entity.EnquiryKind;
import com.companyportal.backend.entity.SalesEnquiry;
import com.companyportal.backend.repository.SalesEnquiryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The website support chat's call-back and demo requests: open without sign-in, checked, saved, quietly dropped when a
 * robot fills the hidden field, and limited per visitor.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"test.context=enquiry", "app.enquiries.per-visitor-per-hour=3"})
class EnquiryTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Kolkata"));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SalesEnquiryRepository repository;

    private ResultActions send(String visitor, String json) throws Exception {
        return mockMvc.perform(post("/api/public/enquiries").header("X-Forwarded-For", visitor)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String callback(String extra) {
        return """
                {"kind":"CALLBACK","name":"Asha Menon","phone":"+91 98470 12345","email":"asha@example.com",
                 "company":"Menon Traders","message":"Pricing"%s}""".formatted(extra);
    }

    @Test
    void callBackIsSavedWithoutSignIn() throws Exception {
        String id = send("10.0.0.1", callback("")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("CALLBACK"))
                .andReturn().getResponse().getContentAsString().replaceAll("\\D*\"id\":(\\d+).*", "$1");
        SalesEnquiry e = repository.findById(Long.valueOf(id)).orElseThrow();
        assertThat(e.getName()).isEqualTo("Asha Menon");
        assertThat(e.getCompany()).isEqualTo("Menon Traders");
        assertThat(e.getMessage()).isEqualTo("Pricing");
        assertThat(e.getDemoDate()).isNull();
        assertThat(e.isNotified()).isFalse();
    }

    @Test
    void demoNeedsAFutureDateAndATime() throws Exception {
        String base = """
                {"kind":"DEMO","name":"Ravi K","phone":"9847012345","email":"ravi@example.com"%s}""";
        send("10.0.0.2", base.formatted("")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("date and time")));
        send("10.0.0.2", base.formatted(",\"demoDate\":\"" + TODAY.minusDays(1) + "\",\"demoTime\":\"11:00\""))
                .andExpect(status().isBadRequest());

        long before = repository.count();
        send("10.0.0.2", base.formatted(",\"demoDate\":\"" + TODAY.plusDays(2) + "\",\"demoTime\":\"11:30\""))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.kind").value("DEMO"));
        assertThat(repository.count()).isEqualTo(before + 1);
        assertThat(repository.findAll()).anySatisfy(e -> {
            assertThat(e.getKind()).isEqualTo(EnquiryKind.DEMO);
            assertThat(e.getDemoDate()).isEqualTo(TODAY.plusDays(2));
            assertThat(e.getDemoTime()).isEqualTo(LocalTime.of(11, 30));
        });
    }

    @Test
    void badContactDetailsAreRefused() throws Exception {
        send("10.0.0.3", callback("").replace("+91 98470 12345", "call me")).andExpect(status().isBadRequest());
        send("10.0.0.3", callback("").replace("asha@example.com", "asha")).andExpect(status().isBadRequest());
        send("10.0.0.3", callback("").replace("\"kind\":\"CALLBACK\"", "\"kind\":\"SPAM\"")).andExpect(status().isBadRequest());
    }

    @Test
    void robotsFillingTheHiddenFieldAreAnsweredButNotKept() throws Exception {
        long before = repository.count();
        send("10.0.0.4", callback(",\"website\":\"http://spam.example\"")).andExpect(status().isCreated());
        assertThat(repository.count()).isEqualTo(before);
    }

    @Test
    void aVisitorMaySendOnlyAFewAnHour() throws Exception {
        for (int i = 0; i < 3; i++) {
            send("10.0.0.5", callback("")).andExpect(status().isCreated());
        }
        send("10.0.0.5", callback("")).andExpect(status().isTooManyRequests());
        // someone else is not held up
        send("10.0.0.6", callback("")).andExpect(status().isCreated());
    }

    @Test
    void theRestOfThePublicAddressesStayClosed() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/public/enquiries"))
                .andExpect(status().isUnauthorized());
    }
}
