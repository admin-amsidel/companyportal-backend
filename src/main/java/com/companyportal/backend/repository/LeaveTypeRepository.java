/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.repository;

import com.companyportal.backend.entity.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, Long> {

    List<LeaveType> findAllByOrderBySortOrderAscNameAsc();

    boolean existsByCodeIgnoreCase(String code);
}
