/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.entity;

/** The paper a Manual Sales worklog entry records, which follows from the status chosen on it. */
public enum ManualSalesKind {
    QUOTATION,
    SALES_ORDER,
    INVOICE;

    /** The kind this project status records; null for a status with no paper, such as Started. */
    public static ManualSalesKind of(String stageCode) {
        return switch (stageCode) {
            case ProjectStage.QUOTATION_ISSUED -> QUOTATION;
            case ProjectStage.SALES_ORDER_ISSUED -> SALES_ORDER;
            case ProjectStage.INVOICE_ISSUED -> INVOICE;
            default -> null;
        };
    }

    public String label() {
        return switch (this) {
            case QUOTATION -> "Quotation";
            case SALES_ORDER -> "Sales order";
            case INVOICE -> "Invoice";
        };
    }
}
