/*
 * Copyright (c) 2026 AMSIDEL Technologies. All rights reserved.
 * Designed and developed by AMSIDEL Technologies.
 */
package com.companyportal.backend.service;

import com.companyportal.backend.dto.NewsDtos.Birthday;
import com.companyportal.backend.dto.NewsDtos.NewsView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Today's news and birthdays for the whole group, before each person's brand filter. Everyone opening the portal
 * (the whole office at 9:00) gets the same lists, so they are read from the database once a minute rather than on
 * every dashboard, each news item also looking up its writer. Any change to news or to an employee clears it at once
 * (and again after commit, so a request in between cannot keep the old lists); the time limit only covers changes
 * made outside this backend.
 */
@Component
public class TodayFeedCache {

    /** One birthday, with the brand it belongs to (null: the group's). */
    public record BirthdayOf(Long brandId, Birthday birthday) {
    }

    public record Today(LocalDate day, List<NewsView> news, List<BirthdayOf> birthdays) {
    }

    private record Kept(Today today, Instant until) {
    }

    private final AtomicReference<Kept> kept = new AtomicReference<>();
    private final Duration ttl;

    public TodayFeedCache(@Value("${app.news.feed-cache-ttl:PT60S}") Duration ttl) {
        this.ttl = ttl;
    }

    /** The lists for this day, from {@code loader} when none are kept (or they are for another day, or old). */
    public Today get(LocalDate day, Supplier<Today> loader) {
        Kept k = kept.get();
        Instant now = Instant.now();
        if (k != null && k.today().day().equals(day) && k.until().isAfter(now)) {
            return k.today();
        }
        Today fresh = loader.get();
        if (!ttl.isZero()) {
            // only if nothing cleared it meanwhile: a change made while loading must not be hidden by these lists
            kept.compareAndSet(k, new Kept(fresh, now.plus(ttl)));
        }
        return fresh;
    }

    /** News or an employee changed: now, and again once the change is committed (or rolled back). */
    public void forget() {
        kept.set(null);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    kept.set(null);
                }
            });
        }
    }
}
