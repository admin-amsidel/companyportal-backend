/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.entity;

/** A purchase bill: being written, received into stock, or cancelled. */
public enum PurchaseStatus {
    DRAFT,
    RECEIVED,
    CANCELLED
}
