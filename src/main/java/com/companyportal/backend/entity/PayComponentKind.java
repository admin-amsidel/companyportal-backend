/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.entity;

/** Where a pay component sits on the payslip. Net pay = earnings - deductions + reimbursements. */
public enum PayComponentKind {
    EARNING,
    DEDUCTION,
    REIMBURSEMENT
}
