/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.repository;

import com.companyportal.backend.entity.WorkHourRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkHourRuleRepository extends JpaRepository<WorkHourRule, Long> {

    List<WorkHourRule> findAllByOrderByLevelNumberAsc();

    Optional<WorkHourRule> findByLevelNumber(int levelNumber);
}
