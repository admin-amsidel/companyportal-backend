/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.repository;

import com.companyportal.backend.entity.OnlineFee;
import com.companyportal.backend.entity.OnlineMethod;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OnlineFeeRepository extends JpaRepository<OnlineFee, OnlineMethod> {
}
