/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.repository;

import com.companyportal.backend.entity.ReviewCycle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ReviewCycleRepository extends JpaRepository<ReviewCycle, Long> {

    /** Newest first. */
    @Query("select c from ReviewCycle c left join fetch c.brand order by c.startDate desc, c.id desc")
    List<ReviewCycle> newestFirst();
}
