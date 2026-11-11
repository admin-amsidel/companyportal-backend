/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.repository;

import com.companyportal.backend.entity.BrandWorkRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BrandWorkRuleRepository extends JpaRepository<BrandWorkRule, Long> {

    Optional<BrandWorkRule> findByBrandKeyAndDesignationKey(Long brandKey, Long designationKey);
}
