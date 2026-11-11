/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.entity;

/** Why stock changed. */
public enum StockMoveKind {
    OPENING,
    PURCHASE,
    PURCHASE_CANCEL,
    SALE,
    SALE_CANCEL,
    ADJUST_IN,
    ADJUST_OUT,
    /** Sent back (to the supplier): less on hand and less received (total in); what went out (sold) stays as it is. */
    RETURN_OUT,
    /** Old: a return added back into stock (briefly offered before RETURN_OUT); kept so the moves saved with it still read. */
    RETURN_IN
}
