/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.security;

import com.companyportal.backend.entity.Employee;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Clears {@link SignedInCache} whenever an employee row is changed or removed: once when the change is written and
 * again after it is committed, so a request running in between cannot keep the old values. (A new reporting line
 * also changes whether the manager leads a team: EmployeeService clears everyone's entry for that.) Today's birthdays on
 * the dashboard ({@link com.companyportal.backend.service.TodayFeedCache}) are cleared the same way.
 */
@Component
@RequiredArgsConstructor
public class EmployeeCacheListener {

    private final SignedInCache cache;
    private final com.companyportal.backend.service.TodayFeedCache todayFeed;

    /** A new person may have their birthday today (the dashboard's birthdays, TodayFeedCache). */
    @PostPersist
    void added(Employee employee) {
        todayFeed.forget();
    }

    @PostUpdate
    @PostRemove
    void changed(Employee employee) {
        Long id = employee.getId();
        cache.evict(id);
        // name, photo, brand, date of birth or leaving change today's birthdays
        todayFeed.forget();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    cache.evict(id);
                }
            });
        }
    }
}
