/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
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
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.io.UnsupportedEncodingException;
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
 * is saved first, then mailed in the background (the visitor never waits for the mail server): an alert to the sales
 * team, and a confirmation to the visitor, both in the portal's mail card (EnquiryMails).
 */
@Slf4j
@Service
public class EnquiryService {

    private static final Duration WINDOW = Duration.ofHours(1);

    private final SalesEnquiryRepository repository;
    private final MailService mailService;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final EnquiryMails mails;
    private final Clock clock;
    private final ZoneId zone;
    private final String[] notifyTo;
    private final int perVisitor;
    private final int perSite;
    private final boolean confirmVisitor;
    private final int maxMailsPerDay;

    /** Guarded by bySite. */
    private final Map<String, Deque<Instant>> byVisitor = new HashMap<>();
    private final Deque<Instant> bySite = new ArrayDeque<>();
    private final ExecutorService mailer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "enquiry-mail");
        t.setDaemon(true);
        return t;
    });

    public EnquiryService(SalesEnquiryRepository repository, MailService mailService,
                          ObjectProvider<JavaMailSender> mailSender, EnquiryMails mails, Clock clock,
                          @Value("${app.leave.zone:Asia/Kolkata}") String zone,
                          @Value("${app.enquiries.notify-to:}") String notifyTo,
                          @Value("${app.enquiries.per-visitor-per-hour:5}") int perVisitor,
                          @Value("${app.enquiries.per-hour:60}") int perSite,
                          @Value("${app.enquiries.confirm-visitor:true}") boolean confirmVisitor,
                          @Value("${app.enquiries.max-mails-per-day:10}") int maxMailsPerDay) {
        this.repository = repository;
        this.mailService = mailService;
        this.mailSender = mailSender;
        this.mails = mails;
        this.clock = clock;
        this.zone = ZoneId.of(zone);
        this.notifyTo = Arrays.stream(notifyTo.split("[,;]")).map(String::trim).filter(StringUtils::hasText)
                .toArray(String[]::new);
        this.perVisitor = perVisitor;
        this.perSite = perSite;
        this.confirmVisitor = confirmVisitor;
        this.maxMailsPerDay = Math.max(0, maxMailsPerDay);
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

        if (notifyTo.length > 0 || confirmVisitor) {
            mailer.execute(() -> sendMails(saved));
        }
        if (notifyTo.length == 0) {
            log.info("Enquiry {} ({}) saved; app.enquiries.notify-to is not set, so the sales team gets no mail",
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

    /** In the background: the sales team's alert (then marked as mailed), and the visitor's confirmation. */
    private void sendMails(SalesEnquiry e) {
        MailSetup setup = mailService.setup();
        JavaMailSender sender = mailSender.getIfAvailable();
        if (!setup.ready() || sender == null) {
            log.warn("Enquiry {} saved but mail is not set up, so no mail was sent", e.getId());
            return;
        }
        if (notifyTo.length > 0) {
            if (!underDailyLimit(e, "sales team")) return;
            try {
                send(sender, setup, mails.toSales(e), notifyTo, e.getEmail());
                repository.markNotified(e.getId());
            } catch (MailException | MessagingException | UnsupportedEncodingException ex) {
                log.warn("Enquiry {} saved but the mail to the sales team failed: {}", e.getId(), ex.getMessage());
            }
        }
        if (confirmVisitor) {
            if (!underDailyLimit(e, "visitor")) return;
            try {
                // replies go to the sales team, not to the no-reply sender
                send(sender, setup, mails.toVisitor(e), new String[]{e.getEmail()}, notifyTo.length > 0 ? notifyTo[0] : null);
                repository.markVisitorNotified(e.getId());
            } catch (MailException | MessagingException | UnsupportedEncodingException ex) {
                log.warn("Enquiry {}: the confirmation to the visitor failed: {}", e.getId(), ex.getMessage());
            }
        }
    }

    /**
     * The chat may send only so many mails a day (app.enquiries.max-mails-per-day, India's day), so a flood of requests
     * cannot use up the mail account's daily quota that staff mail needs. Past it the enquiry is still saved and shows
     * in the portal (Website enquiries, and the notification bell); only the mail is skipped.
     */
    private boolean underDailyLimit(SalesEnquiry e, String to) {
        java.time.Instant dayStart = LocalDate.now(clock.withZone(zone)).atStartOfDay(zone).toInstant();
        long sent = repository.mailsSince(dayStart);
        if (sent >= maxMailsPerDay) {
            log.warn("Enquiry {} saved; no mail to the {}: today's limit of {} chat mails is reached", e.getId(), to, maxMailsPerDay);
            return false;
        }
        return true;
    }

    private static void send(JavaMailSender sender, MailSetup setup, EnquiryMails.Mail mail, String[] to, String replyTo)
            throws MessagingException, UnsupportedEncodingException {
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper m = new MimeMessageHelper(message, true, "UTF-8");
        m.setFrom(setup.from(), setup.fromName());
        m.setTo(to);
        if (StringUtils.hasText(replyTo)) m.setReplyTo(replyTo);
        m.setSubject(mail.subject());
        m.setText(mail.plain(), mail.html());
        sender.send(message);
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    @PreDestroy
    void stop() {
        mailer.shutdown();
    }
}
