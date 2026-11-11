/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.entity;

/** OPEN tasks can be logged against; CLOSED ones keep their history but take no new hours. */
public enum TaskStatus {
    OPEN,
    CLOSED
}
