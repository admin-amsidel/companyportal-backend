/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.entity;

/**
 * What a signed-in user may do. OWNER sees the whole group; ADMIN manages people in their brand
 * (or in every brand when they have none); MANAGER and EMPLOYEE only see their own record for now.
 */
public enum Role {
    OWNER,
    ADMIN,
    MANAGER,
    EMPLOYEE
}
