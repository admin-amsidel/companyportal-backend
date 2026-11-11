/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.entity;

/**
 * How a salary's income tax is set: NEW or OLD regime = worked out from the salary (the Income Tax line follows it);
 * MANUAL = typed in by someone allowed to (the Manual income tax checkpoint).
 */
public enum TaxRegime {
    NEW,
    OLD,
    MANUAL
}
