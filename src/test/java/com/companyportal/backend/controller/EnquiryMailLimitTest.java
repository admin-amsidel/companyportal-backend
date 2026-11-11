/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.controller;

import com.companyportal.backend.entity.SalesEnquiry;
import com.companyportal.backend.repository.SalesEnquiryRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The website chat's daily mail limit: mails go out (to a fake mail server here) until the day's limit, then stop;
 * every enquiry is still saved.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"test.context=enquirylimit", "spring.mail.host=localhost", "spring.mail.username=",
        "app.mail.from=portal@example.com", "app.enquiries.notify-to=sales@example.com",
        "app.enquiries.confirm-visitor=true", "app.enquiries.max-mails-per-day=3"})
class EnquiryMailLimitTest {

    /** Keeps the mails instead of sending them. */
    static class FakeMailSender extends JavaMailSenderImpl {
        final List<String> sentTo = new CopyOnWriteArrayList<>();

        @Override
        protected void doSend(MimeMessage[] messages, Object[] originals) {
            for (MimeMessage m : messages) {
                try {
                    sentTo.add(m.getAllRecipients()[0].toString());
                } catch (MessagingException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
    }

    @TestConfiguration
    static class FakeMail {
        @Bean
        FakeMailSender javaMailSender() {
            return new FakeMailSender();
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private FakeMailSender mail;
    @Autowired
    private SalesEnquiryRepository repository;

    @Test
    void mailsStopAtTheDailyLimitButEveryEnquiryIsKept() throws Exception {
        for (int i = 1; i <= 3; i++) {
            mockMvc.perform(post("/api/public/enquiries").header("X-Forwarded-For", "10.7.0." + i).contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"kind":"CALLBACK","name":"Visitor %d","phone":"98470 1234%d","email":"v%d@example.com"}""".formatted(i, i, i)))
                    .andExpect(status().isCreated());
            // the mails go out in the background, one enquiry after another
            waitFor(i == 1 ? 2 : 3);
        }
        Thread.sleep(300);
        // the first: sales team and visitor; the second: the sales team (the 3rd mail); then nothing
        assertThat(mail.sentTo).containsExactly("sales@example.com", "v1@example.com", "sales@example.com");
        List<SalesEnquiry> all = repository.findAll();
        assertThat(all).hasSize(3);
        assertThat(all.stream().filter(SalesEnquiry::isNotified)).hasSize(2);
        assertThat(all.stream().filter(SalesEnquiry::isVisitorNotified)).hasSize(1);
        assertThat(repository.mailsSince(java.time.Instant.EPOCH)).isEqualTo(3);
    }

    private void waitFor(int mails) throws InterruptedException {
        for (int i = 0; i < 100 && mail.sentTo.size() < mails; i++) {
            Thread.sleep(50);
        }
        // and for the mark that the mail went out
        Thread.sleep(100);
    }
}
