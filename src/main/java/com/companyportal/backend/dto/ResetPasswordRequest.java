/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.dto;

import com.companyportal.backend.validation.Pin;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Admin sets a temporary PIN; the employee must change it at next login. */
@Data
public class ResetPasswordRequest {

    @NotBlank
    @Pin
    private String newPassword;
}
