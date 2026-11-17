/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.repository;

import com.companyportal.backend.entity.EmployeeSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EmployeeSkillRepository extends JpaRepository<EmployeeSkill, Long> {

    /** One person's skills, in their order. */
    @Query("select s from EmployeeSkill s where s.employee.id = :employeeId order by s.sortOrder, s.id")
    List<EmployeeSkill> ofEmployee(@Param("employeeId") Long employeeId);

    /** The skill names people have listed, most used first: suggestions for the next person. */
    @Query("select s.name from EmployeeSkill s group by s.name order by count(s) desc, s.name")
    List<String> namesInUse();
}
