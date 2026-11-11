/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.dto;

import com.companyportal.backend.entity.LeaveAllocation;

import java.math.BigDecimal;

/**
 * One leave type's balance for one person in a leave plan. entitled (the maximum) = quota + carried + granted;
 * available (what remains) = entitled - used - pending. For UNLIMITED types (leave without pay) entitled and available
 * are null. carried: what came on from the brand's plan before.
 */
public record LeaveBalance(
        Long leaveTypeId,
        String code,
        String name,
        LeaveAllocation allocation,
        boolean halfDayAllowed,
        boolean restrictedDatesOnly,
        int quota,
        BigDecimal carried,
        BigDecimal granted,
        BigDecimal entitled,
        BigDecimal used,
        BigDecimal pending,
        BigDecimal available) {
}
