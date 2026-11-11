/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.service;

import com.companyportal.backend.entity.EnquiryKind;
import com.companyportal.backend.entity.SalesEnquiry;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/** The website enquiry mails: the staff mail card, the details, the buttons, and what visitors typed escaped. */
class EnquiryMailsTest {

    private final EnquiryMails mails = new EnquiryMails("Asia/Kolkata", "COMPANY PORTAL", "IANEL Technologies",
            "+91 98470 00000", "https://portal.example.com/");

    private static SalesEnquiry demo() {
        SalesEnquiry e = new SalesEnquiry();
        e.setId(42L);
        e.setKind(EnquiryKind.DEMO);
        e.setName("Asha <b>Menon</b>");
        e.setPhone("+91 98470 12345");
        e.setEmail("asha@example.com");
        e.setCompany("Menon & Sons");
        e.setMessage("Pricing; Payroll & Payslip");
        e.setDemoDate(LocalDate.of(2026, 11, 12));
        e.setDemoTime(LocalTime.of(15, 30));
        e.setCreatedAt(Instant.parse("2026-11-10T11:22:00Z"));
        return e;
    }

    @Test
    void salesTeamGetsTheDetailsInTheMailCard() throws Exception {
        EnquiryMails.Mail m = mails.toSales(demo());
        assertThat(m.subject()).isEqualTo("New demo request: Asha <b>Menon</b> (Menon & Sons)");
        assertThat(m.html())
                .contains("background:#eef4fb", "COMPANY PORTAL", "New demo request")
                .contains("Thursday, 12 November 2026 at 3:30 pm (India time)")
                .contains("Asha &lt;b&gt;Menon&lt;/b&gt;").doesNotContain("<b>Menon</b>")
                .contains("Menon &amp; Sons", "Pricing; Payroll &amp; Payslip", "10 Nov 2026, 4:52 PM")
                .contains("href=\"tel:+919847012345\"", "href=\"mailto:asha@example.com\"")
                .contains("href=\"https://portal.example.com/portal/admin/enquiries\"")
                .contains("Website enquiry no. 42");
        assertThat(m.plain()).contains("NEW DEMO REQUEST", "Phone: +91 98470 12345", "Interested in: Pricing; Payroll & Payslip");
        preview("enquiry-mail-sales.html", m.html());
    }

    @Test
    void visitorGetsAThankYouWithWhatWasReceived() throws Exception {
        EnquiryMails.Mail m = mails.toVisitor(demo());
        assertThat(m.subject()).isEqualTo("Your Company Portal demo request");
        assertThat(m.html())
                .contains("Dear Asha,", "Thursday, 12 November 2026 at 3:30 pm", "Our service team will contact you shortly")
                .contains("+91 98470 00000", "Team IANEL Technologies")
                // what sales notes for themselves is not shown to the visitor
                .doesNotContain("Pricing; Payroll", "portal/admin");
        assertThat(m.plain()).startsWith("Dear Asha,").contains("Warm regards,\nTeam IANEL Technologies");

        SalesEnquiry callBack = demo();
        callBack.setKind(EnquiryKind.CALLBACK);
        callBack.setDemoDate(null);
        callBack.setDemoTime(null);
        EnquiryMails.Mail c = mails.toVisitor(callBack);
        assertThat(c.subject()).isEqualTo("We have received your request");
        assertThat(c.html()).contains("our customer agent will contact you back shortly").doesNotContain("India time");
        preview("enquiry-mail-visitor.html", m.html());
    }

    @Test
    void withoutAPortalAddressThereIsNoPortalButton() {
        EnquiryMails plain = new EnquiryMails("Asia/Kolkata", "COMPANY PORTAL", "IANEL Technologies", "", "");
        assertThat(plain.toSales(demo()).html()).doesNotContain("Open in the portal");
        assertThat(plain.toVisitor(demo()).html()).doesNotContain("call us on");
    }

    /** Written next to the build output, to look at in a browser. */
    private static void preview(String name, String html) throws Exception {
        Path dir = Path.of("target", "mail-previews");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(name), html);
    }
}
