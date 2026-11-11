/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.repository;

import com.companyportal.backend.entity.SalesEnquiry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface SalesEnquiryRepository extends JpaRepository<SalesEnquiry, Long> {

    /** Newest first; the list is small (a few a day), so the page filters and searches it itself. */
    @Query("select e from SalesEnquiry e order by e.createdAt desc")
    List<SalesEnquiry> newestFirst();

    @Modifying
    @Transactional
    @Query("update SalesEnquiry e set e.notified = true where e.id = :id")
    void markNotified(@Param("id") Long id);

    @Modifying
    @Transactional
    @Query("update SalesEnquiry e set e.visitorNotified = true where e.id = :id")
    void markVisitorNotified(@Param("id") Long id);

    /** Mails the website chat sent for enquiries made since then (sales team's and visitors'), for the daily limit. */
    @Query("select coalesce(sum(case when e.notified = true then 1 else 0 end)"
            + " + sum(case when e.visitorNotified = true then 1 else 0 end), 0) from SalesEnquiry e where e.createdAt >= :since")
    long mailsSince(@Param("since") java.time.Instant since);

    long countByStatus(com.companyportal.backend.entity.EnquiryStatus status);
}
