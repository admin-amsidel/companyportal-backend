/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.repository;

import com.companyportal.backend.entity.ProfessionalTaxSlab;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProfessionalTaxSlabRepository extends JpaRepository<ProfessionalTaxSlab, Long> {

    /** Lowest first. */
    @Query("select s from ProfessionalTaxSlab s order by s.fromIncome")
    List<ProfessionalTaxSlab> ordered();
}
