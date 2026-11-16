/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.entity;

/** CANCELLED: by the person themself. REVOKED: approved leave taken back by an owner or admin before it started. Both give the days back. */
public enum LeaveStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED,
    REVOKED
}
