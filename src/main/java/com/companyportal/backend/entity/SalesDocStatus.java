/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.entity;

/** Quotations: DRAFT, SENT, ACCEPTED, REJECTED. Invoices: DRAFT, ISSUED, CANCELLED. */
public enum SalesDocStatus {
    DRAFT,
    SENT,
    ACCEPTED,
    REJECTED,
    ISSUED,
    CANCELLED
}
