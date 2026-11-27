/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.windowquote;

/** How a single panel (cell) of the layout opens. L/R = hinge or slide side, seen from outside. */
public enum OpeningType {
    FIXED,
    CASEMENT_L, CASEMENT_R,
    TILT_TURN_L, TILT_TURN_R,
    TOP_HUNG,
    SLIDING_L, SLIDING_R,
    DOOR_L, DOOR_R;

    public boolean isOpening() { return this != FIXED; }

    public boolean isSliding() { return this == SLIDING_L || this == SLIDING_R; }
}
