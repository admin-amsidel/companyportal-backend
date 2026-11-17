/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.repository;

import com.companyportal.backend.entity.PayrollTaxYear;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PayrollTaxYearRepository extends JpaRepository<PayrollTaxYear, Long> {

    @Query("select t from PayrollTaxYear t where t.employee.id = :employeeId and t.fyStartYear = :fy")
    Optional<PayrollTaxYear> find(@Param("employeeId") Long employeeId, @Param("fy") int fy);

    @Query("select t from PayrollTaxYear t where t.fyStartYear = :fy")
    List<PayrollTaxYear> ofYear(@Param("fy") int fy);
}
