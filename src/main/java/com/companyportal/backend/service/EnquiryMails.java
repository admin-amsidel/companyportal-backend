/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.service;

import com.companyportal.backend.entity.EnquiryKind;
import com.companyportal.backend.entity.SalesEnquiry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.companyportal.backend.service.MailService.CARD_BLUE;
import static com.companyportal.backend.service.MailService.CARD_INK;
import static com.companyportal.backend.service.MailService.CARD_MUTED;

/**
 * The two mails of a website enquiry, in the same card as staff mail (MailService.frame): the alert to the sales team
 * and the "we have your request" note to the visitor. Each has a plain-text part for mail apps without HTML.
 */
@Component
public class EnquiryMails {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter RECEIVED = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH);

    public record Mail(String subject, String plain, String html) {
    }

    private final ZoneId zone;
    private final String header;
    private final String company;
    private final String phone;
    private final String portalUrl;

    public EnquiryMails(@Value("${app.leave.zone:Asia/Kolkata}") String zone,
                        @Value("${app.enquiries.mail-header:COMPANY PORTAL}") String header,
                        @Value("${app.enquiries.company-name:IANEL Technologies}") String company,
                        @Value("${app.enquiries.phone:}") String phone,
                        @Value("${app.enquiries.portal-url:}") String portalUrl) {
        this.zone = ZoneId.of(zone);
        this.header = header;
        this.company = company;
        this.phone = phone == null ? "" : phone.trim();
        this.portalUrl = portalUrl == null ? "" : portalUrl.trim().replaceAll("/+$", "");
    }

    /** To the sales team: who asked for what, with buttons to call and answer. */
    public Mail toSales(SalesEnquiry e) {
        boolean demo = e.getKind() == EnquiryKind.DEMO;
        String what = demo ? "demo" : "call back";
        String subject = (demo ? "New demo request: " : "New call-back request: ") + e.getName()
                + (e.getCompany() != null ? " (" + e.getCompany() + ")" : "");

        List<String[]> rows = details(e, true);
        StringBuilder body = new StringBuilder();
        body.append(title(demo ? "New demo request" : "New call-back request"));
        body.append(p("A visitor asked for a " + what + " through the website chat. Please contact them"
                + (demo ? " to confirm the demo." : " soon.")));
        body.append(table(rows));
        List<String> buttons = new ArrayList<>();
        buttons.add(button("tel:" + e.getPhone().replaceAll("[^\\d+]", ""), "Call " + e.getPhone(), true));
        buttons.add(button("mailto:" + e.getEmail(), "Reply by email", false));
        if (!portalUrl.isEmpty()) {
            buttons.add(button(portalUrl + "/portal/admin/enquiries", "Open in the portal", false));
        }
        body.append("<p style=\"margin:22px 0 4px\">").append(String.join("&nbsp;&nbsp;", buttons)).append("</p>");
        body.append(muted("Record the follow-up under Administration &gt; Website enquiries, so the team knows it is taken."));

        StringBuilder plain = new StringBuilder(demo ? "NEW DEMO REQUEST" : "NEW CALL-BACK REQUEST").append("\n\n")
                .append("A visitor asked for a ").append(what).append(" through the website chat.\n\n");
        rows.forEach(r -> plain.append(r[0]).append(": ").append(r[1]).append('\n'));
        if (!portalUrl.isEmpty()) plain.append("\nOpen in the portal: ").append(portalUrl).append("/portal/admin/enquiries\n");
        return new Mail(subject, plain.toString(),
                MailService.frame(header, body.toString(), "Website enquiry no. " + e.getId() + " · from the support chat on the website"));
    }

    /** To the visitor: thanks, what we received, and what happens next. */
    public Mail toVisitor(SalesEnquiry e) {
        boolean demo = e.getKind() == EnquiryKind.DEMO;
        String first = e.getName().trim().split("\\s+")[0];
        String subject = demo ? "Your Company Portal demo request" : "We have received your request";

        StringBuilder body = new StringBuilder();
        body.append("<p style=\"margin:0 0 16px\">Dear ").append(esc(first)).append(",</p>");
        if (demo) {
            body.append(p("Thank you for your interest in Company Portal. We have received your request for a live demo on <b>"
                    + esc(when(e)) + "</b> (India time)."));
            body.append(p("Our service team will contact you shortly to confirm the time and share the meeting link."));
        } else {
            body.append(p("Thank you for your interest in Company Portal. We have received your request, and our customer"
                    + " agent will contact you back shortly."));
        }
        body.append(title("Your request"));
        body.append(table(details(e, false)));
        body.append(p("If any of these details are wrong, simply reply to this email."
                + (phone.isEmpty() ? "" : " You can also call us on <b>" + esc(phone) + "</b>.")));
        body.append("<div style=\"margin:28px 0 0;padding-top:14px;border-top:1px solid #e3ecf7;color:").append(CARD_MUTED)
                .append("\">Warm regards,<br><b style=\"color:").append(CARD_INK).append("\">Team ").append(esc(company))
                .append("</b></div>");

        StringBuilder plain = new StringBuilder("Dear ").append(first).append(",\n\n");
        plain.append(demo
                ? "Thank you for your interest in Company Portal. We have received your request for a live demo on "
                + when(e) + " (India time). Our service team will contact you shortly to confirm.\n\n"
                : "Thank you for your interest in Company Portal. We have received your request, and our customer agent"
                + " will contact you back shortly.\n\n");
        plain.append("Your request:\n");
        details(e, false).forEach(r -> plain.append("  ").append(r[0]).append(": ").append(r[1]).append('\n'));
        plain.append("\nIf any of these details are wrong, simply reply to this email.");
        if (!phone.isEmpty()) plain.append(" You can also call us on ").append(phone).append('.');
        plain.append("\n\nWarm regards,\nTeam ").append(company).append('\n');
        return new Mail(subject, plain.toString(),
                MailService.frame(header, body.toString(), company + " · You are receiving this because you contacted us through our website."));
    }

    // ------------------------------------------------------------------ parts

    /** Label and value pairs (values not escaped yet). */
    private List<String[]> details(SalesEnquiry e, boolean forSales) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Request", e.getKind() == EnquiryKind.DEMO ? "Live demo" : "Call back"});
        if (e.getKind() == EnquiryKind.DEMO) rows.add(new String[]{"Demo", when(e) + " (India time)"});
        rows.add(new String[]{"Name", e.getName()});
        rows.add(new String[]{"Phone", e.getPhone()});
        rows.add(new String[]{"Email", e.getEmail()});
        if (StringUtils.hasText(e.getCompany())) rows.add(new String[]{"Company", e.getCompany()});
        if (forSales) {
            if (StringUtils.hasText(e.getMessage())) rows.add(new String[]{"Interested in", e.getMessage()});
            rows.add(new String[]{"Received", RECEIVED.format(e.getCreatedAt().atZone(zone))});
        }
        return rows;
    }

    private String when(SalesEnquiry e) {
        LocalTime t = e.getDemoTime();
        return DAY.format(e.getDemoDate()) + (t != null ? " at " + TIME.format(t).replace("AM", "am").replace("PM", "pm") : "");
    }

    private static String table(List<String[]> rows) {
        StringBuilder t = new StringBuilder("<table role=\"presentation\" width=\"100%\" style=\"border-collapse:collapse;"
                + "margin:4px 0 8px;background:#f7faff;border:1px solid #dbe7f5;border-radius:8px\">");
        for (int i = 0; i < rows.size(); i++) {
            String border = i == rows.size() - 1 ? "" : "border-bottom:1px solid #e6eef8;";
            t.append("<tr><td style=\"").append(border).append("padding:9px 14px;width:130px;font-size:13px;color:").append(CARD_MUTED)
                    .append(";vertical-align:top\">").append(esc(rows.get(i)[0])).append("</td><td style=\"").append(border)
                    .append("padding:9px 14px;font-size:14px;color:").append(CARD_INK).append(";font-weight:600\">")
                    .append(esc(rows.get(i)[1])).append("</td></tr>");
        }
        return t.append("</table>").toString();
    }

    private static String button(String href, String label, boolean primary) {
        return "<a href=\"" + esc(href) + "\" style=\"display:inline-block;margin:0 0 8px;padding:10px 18px;border-radius:6px;"
                + "font-size:14px;font-weight:700;text-decoration:none;"
                + (primary ? "background:" + CARD_BLUE + ";color:#ffffff;border:1px solid " + CARD_BLUE
                : "background:#ffffff;color:" + CARD_BLUE + ";border:1px solid #9fc1e6") + "\">" + esc(label) + "</a>";
    }

    private static String title(String text) {
        return "<div style=\"margin:0 0 10px;font-size:18px;line-height:1.35;font-weight:700;color:" + CARD_BLUE + "\">"
                + esc(text) + "</div>";
    }

    /** A paragraph of HTML that is already escaped (or holds only our own tags). */
    private static String p(String html) {
        return "<p style=\"margin:0 0 14px\">" + html + "</p>";
    }

    private static String muted(String html) {
        return "<p style=\"margin:10px 0 0;font-size:13px;color:" + CARD_MUTED + "\">" + html + "</p>";
    }

    private static String esc(String s) {
        return HtmlUtils.htmlEscape(s);
    }
}
