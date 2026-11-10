/*
 * Copyright (c) 2026 AMSIDEL Technologies. All rights reserved.
 * Designed and developed by AMSIDEL Technologies.
 */
package com.companyportal.backend.entity;

/** How far the sales team has got with a website enquiry (Administration > Website enquiries). */
public enum EnquiryStatus {
    /** Just arrived: nobody has called yet. */
    NEW,
    /** Someone spoke to them or wrote back. */
    CONTACTED,
    /** A demo date is agreed (or the requested one confirmed). */
    DEMO_SCHEDULED,
    /** They became a customer. */
    WON,
    /** Not interested, unreachable or spam. */
    CLOSED
}
