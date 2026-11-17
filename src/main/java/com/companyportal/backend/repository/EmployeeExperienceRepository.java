/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.repository;

import com.companyportal.backend.entity.EmployeeExperience;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EmployeeExperienceRepository extends JpaRepository<EmployeeExperience, Long> {

    /** One person's jobs, the latest first (still working there first of all). */
    @Query("select x from EmployeeExperience x where x.employee.id = :employeeId"
            + " order by case when x.endMonth is null then 0 else 1 end, x.endMonth desc, x.startMonth desc, x.id desc")
    List<EmployeeExperience> ofEmployee(@Param("employeeId") Long employeeId);
}
