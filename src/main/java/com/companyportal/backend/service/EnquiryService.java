/*
 * Copyright (c) 2026 AMSIDEL Technologies. All rights reserved.
 * Designed and developed by AMSIDEL Technologies.
 */
package com.companyportal.backend.service;

import com.companyportal.backend.dto.EnquiryDtos.EnquiryReceived;
import com.companyportal.backend.dto.EnquiryDtos.EnquiryRequest;
import com.companyportal.backend.dto.MailSetup;
import com.companyportal.backend.entity.EnquiryKind;
import com.companyportal.backend.entity.SalesEnquiry;
import com.companyportal.backend.exception.ApiException;
import com.companyportal.backend.repository.SalesEnquiryRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Call-back and demo requests from the website's support chat. Open to anyone, so: robots that fill the hidden field
 * get a polite answer and nothing is kept; a visitor may leave a few an hour and the whole site a few dozen. Each one
 * is saved first, then mailed to the sales team in the background (the visitor never waits for the mail server).
 */
@Slf4j
@Service
public class EnquiryService {

    private static final Duration WINDOW = Duration.ofHours(1);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy");

    private final SalesEnquiryRepository repository;
    private final MailService mailService;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final Clock clock;
    private final ZoneId zone;
    private final String[] notifyTo;
    private final int perVisitor;
    private final int perSite;

    /** Guarded by bySite. */
    private final Map<String, Deque<Instant>> byVisitor = new HashMap<>();
    private final Deque<Instant> bySite = new ArrayDeque<>();
    private final ExecutorService mailer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "enquiry-mail");
        t.setDaemon(true);
        return t;
    });

    public EnquiryService(SalesEnquiryRepository repository, MailService mailService,
                          ObjectProvider<JavaMailSender> mailSender, Clock clock,
                          @Value("${app.leave.zone:Asia/Kolkata}") String zone,
                          @Value("${app.enquiries.notify-to:}") String notifyTo,
                          @Value("${app.enquiries.per-visitor-per-hour:5}") int perVisitor,
                          @Value("${app.enquiries.per-hour:60}") int perSite) {
        this.repository = repository;
        this.mailService = mailService;
        this.mailSender = mailSender;
        this.clock = clock;
        this.zone = ZoneId.of(zone);
        this.notifyTo = Arrays.stream(notifyTo.split("[,;]")).map(String::trim).filter(StringUtils::hasText)
                .toArray(String[]::new);
        this.perVisitor = perVisitor;
        this.perSite = perSite;
    }

    public EnquiryReceived receive(EnquiryRequest req, String visitor) {
        if (StringUtils.hasText(req.website())) {
            // the hidden field: a robot. Answer as if it worked, so it does not try again another way
            return new EnquiryReceived(0L, req.kind());
        }
        checkDemo(req);
        admit(visitor);

        SalesEnquiry e = new SalesEnquiry();
        e.setKind(req.kind());
        e.setName(req.name().trim());
        e.setPhone(req.phone().trim());
        e.setEmail(req.email().trim());
        e.setCompany(blankToNull(req.company()));
        e.setMessage(blankToNull(req.message()));
        if (req.kind() == EnquiryKind.DEMO) {
            e.setDemoDate(req.demoDate());
            e.setDemoTime(req.demoTime());
        }
        e.setCreatedAt(clock.instant());
        SalesEnquiry saved = repository.save(e);

        if (notifyTo.length > 0) {
            mailer.execute(() -> notifySales(saved));
        } else {
            log.info("Enquiry {} ({}) saved; app.enquiries.notify-to is not set, so no mail was sent",
                    saved.getId(), saved.getKind());
        }
        return new EnquiryReceived(saved.getId(), saved.getKind());
    }

    private void checkDemo(EnquiryRequest req) {
        if (req.kind() != EnquiryKind.DEMO) return;
        if (req.demoDate() == null || req.demoTime() == null) {
            throw ApiException.badRequest("Choose a date and time for the demo.");
        }
        LocalDate today = LocalDate.now(clock.withZone(zone));
        if (req.demoDate().isBefore(today) || req.demoDate().isAfter(today.plusDays(180))) {
            throw ApiException.badRequest("Choose a demo date from today up to six months ahead.");
        }
    }

    /** Throws 429 when this visitor, or the whole site, has sent too many in the last hour. */
    private void admit(String visitor) {
        Instant now = clock.instant();
        Instant since = now.minus(WINDOW);
        synchronized (bySite) {
            while (!bySite.isEmpty() && bySite.peekFirst().isBefore(since)) bySite.pollFirst();
            if (bySite.size() >= perSite) throw tooMany();
            if (byVisitor.size() > 10_000) byVisitor.clear();
            Deque<Instant> mine = byVisitor.computeIfAbsent(visitor, k -> new ArrayDeque<>());
            while (!mine.isEmpty() && mine.peekFirst().isBefore(since)) mine.pollFirst();
            if (mine.size() >= perVisitor) throw tooMany();
            mine.addLast(now);
            bySite.addLast(now);
        }
    }

    private static ApiException tooMany() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                "We have already received your details. Please try again later, or call us.");
    }

    private void notifySales(SalesEnquiry e) {
        try {
            MailSetup setup = mailService.setup();
            JavaMailSender sender = mailSender.getIfAvailable();
            if (!setup.ready() || sender == null) {
                log.warn("Enquiry {} saved but mail is not set up, so the sales team was not told", e.getId());
                return;
            }
            boolean demo = e.getKind() == EnquiryKind.DEMO;
            SimpleMailMessage m = new SimpleMailMessage();
            m.setFrom(setup.from());
            m.setTo(notifyTo);
            m.setReplyTo(e.getEmail());
            m.setSubject((demo ? "Demo request: " : "Call-back request: ") + e.getName()
                    + (e.getCompany() != null ? " (" + e.getCompany() + ")" : ""));
            StringBuilder b = new StringBuilder();
            b.append(demo ? "A visitor asked for a demo" : "A visitor asked to be contacted")
                    .append(" through the website chat.\n\n");
            b.append("Name:    ").append(e.getName()).append('\n');
            b.append("Phone:   ").append(e.getPhone()).append('\n');
            b.append("Email:   ").append(e.getEmail()).append('\n');
            if (e.getCompany() != null) b.append("Company: ").append(e.getCompany()).append('\n');
            if (demo) {
                b.append("Demo:    ").append(DAY.format(e.getDemoDate())).append(" at ").append(e.getDemoTime())
                        .append(" (").append(zone.getId()).append(")\n");
            }
            if (e.getMessage() != null) b.append("\nInterested in: ").append(e.getMessage()).append('\n');
            b.append("\nReply to this mail to answer them. Enquiry no. ").append(e.getId()).append('.');
            m.setText(b.toString());
            sender.send(m);
            repository.markNotified(e.getId());
        } catch (RuntimeException ex) {
            log.warn("Enquiry {} saved but the mail to the sales team failed: {}", e.getId(), ex.getMessage());
        }
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    @PreDestroy
    void stop() {
        mailer.shutdown();
    }
}
