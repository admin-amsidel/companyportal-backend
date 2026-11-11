/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** Optional note from the approver, shown to the employee (required when rejecting). */
@Data
public class LeaveDecisionRequest {

    @Size(max = 300)
    private String note;
}
