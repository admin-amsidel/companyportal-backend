/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.entity;

/** Quotation or invoice. */
public enum SalesDocKind {
    QUOTATION,
    /** The customer's confirmed order (from a quotation, items removed or changed), before the invoice. */
    SALES_ORDER,
    INVOICE
}
