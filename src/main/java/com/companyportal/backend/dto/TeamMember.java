/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.dto;

import com.companyportal.backend.entity.Employee;
import com.companyportal.backend.entity.Role;

/** Someone whose leave the signed-in approver decides; for the grant form's person picker. */
public record TeamMember(Long id, String name, String empNumber, Role role, String brandCode, String brandName) {

    /** Call inside a transaction: reads the brand. */
    public static TeamMember from(Employee e) {
        return new TeamMember(e.getId(), e.getName(), e.getEmpNumber(), e.getRole(),
                e.getBrand() == null ? null : e.getBrand().getCode(),
                e.getBrand() == null ? null : e.getBrand().getName());
    }
}
