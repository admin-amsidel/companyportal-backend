/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.entity;

/** What a visitor of the website asked for through the support chat. */
public enum EnquiryKind {
    /** "Call me back": a sales person contacts them about features or pricing. */
    CALLBACK,
    /** A demo at the date and time they chose; the service team confirms it. */
    DEMO
}
