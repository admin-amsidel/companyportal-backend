/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.repository;

import com.companyportal.backend.entity.ProjectStage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectStageRepository extends JpaRepository<ProjectStage, String> {

    List<ProjectStage> findAllByOrderBySortOrderAscNameAsc();
}
