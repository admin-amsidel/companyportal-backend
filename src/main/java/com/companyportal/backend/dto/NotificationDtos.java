/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.dto;

import com.companyportal.backend.entity.NotificationClear;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** The notification bell. */
public final class NotificationDtos {

    private NotificationDtos() {
    }

    /** pollMinutes: how long the page waits before asking again, from the server's settings. */
    /** newEnquiries: website enquiries nobody has followed up yet (for those with Website enquiries; else 0). */
    public record Feed(int pollMinutes, List<Item> items, long newEnquiries) {
    }

    /** kind STARTING with started false: about to start; started true: under way. CLOSED: it has ended. */
    public record Item(Long meetingId, NotificationClear.Kind kind, boolean started, String title, LocalDate date, LocalTime startTime,
                       LocalTime endTime, String location) {
    }
}
