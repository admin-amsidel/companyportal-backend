/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.repository;

import com.companyportal.backend.entity.SalaryStructure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SalaryStructureRepository extends JpaRepository<SalaryStructure, Long> {

    /** Every salary revision of one financial year, with its lines and their components, oldest first. */
    @Query("select distinct s from SalaryStructure s left join fetch s.lines l left join fetch l.component"
            + " left join fetch s.updatedBy where s.fyStartYear = :fy order by s.effectiveFrom")
    List<SalaryStructure> ofYear(@Param("fy") int fy);

    /** One person's salary revisions of a financial year, oldest first. */
    @Query("select distinct s from SalaryStructure s left join fetch s.lines l left join fetch l.component"
            + " left join fetch s.updatedBy where s.employee.id = :employeeId and s.fyStartYear = :fy order by s.effectiveFrom")
    List<SalaryStructure> revisions(@Param("employeeId") Long employeeId, @Param("fy") int fy);

    /** Financial years that have any salary, newest first. */
    @Query("select distinct s.fyStartYear from SalaryStructure s order by s.fyStartYear desc")
    List<Integer> years();
}
