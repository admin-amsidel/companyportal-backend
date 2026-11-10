/*
 * Copyright (c) 2026 AMSIDEL Technologies. All rights reserved.
 * Designed and developed by AMSIDEL Technologies.
 */
package com.companyportal.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * A call-back or demo request left by a visitor in the website's support chat (no sign-in). Kept so that none is lost
 * when the notification mail cannot be sent; the sales team is told by mail (app.enquiries.notify-to).
 */
@Entity
@Table(name = "sales_enquiries", indexes = {
        @Index(name = "ix_sales_enquiry_created", columnList = "createdAt"),
        @Index(name = "ix_sales_enquiry_status", columnList = "status")})
@Getter
@Setter
@NoArgsConstructor
public class SalesEnquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EnquiryKind kind;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, length = 120)
    private String email;

    @Column(length = 120)
    private String company;

    /** What they were reading about in the chat (Pricing, a module...), or what they typed. */
    @Column(length = 500)
    private String message;

    /** For a demo: the day and time they asked for (India time). */
    private LocalDate demoDate;

    private LocalTime demoTime;

    /** Whether the notification mail went out. */
    @Column(nullable = false)
    private boolean notified;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** Where the sales team is with it. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EnquiryStatus status = EnquiryStatus.NEW;

    /** The sales team's own notes: what was said, the next step. */
    @Column(length = 1000)
    private String notes;

    /** Who last changed the status or notes, and when. */
    @Column(length = 120)
    private String updatedByName;

    private Instant updatedAt;
}
