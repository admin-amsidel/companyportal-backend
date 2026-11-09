/*
 * Copyright (c) 2026 AMSIDEL Technologies. All rights reserved.
 * Designed and developed by AMSIDEL Technologies.
 */
package com.companyportal.backend.dto;

import com.companyportal.backend.validation.Pin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordRequest {

    @NotBlank
    @Size(max = 64)
    private String currentPassword;

    @NotBlank
    @Pin
    private String newPassword;
}
