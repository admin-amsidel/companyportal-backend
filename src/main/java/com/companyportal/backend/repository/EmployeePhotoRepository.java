/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.repository;

import com.companyportal.backend.entity.EmployeePhoto;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeePhotoRepository extends JpaRepository<EmployeePhoto, Long> {
}
