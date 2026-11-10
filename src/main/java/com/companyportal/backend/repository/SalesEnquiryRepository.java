/*
 * Copyright (c) 2026 AMSIDEL Technologies. All rights reserved.
 * Designed and developed by AMSIDEL Technologies.
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
}
