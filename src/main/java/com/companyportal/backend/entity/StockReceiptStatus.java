/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.entity;

/** A scanned stock receipt: waiting for someone to check it, taken into stock, or turned down. */
public enum StockReceiptStatus {
    PENDING,
    APPROVED,
    REJECTED
}
