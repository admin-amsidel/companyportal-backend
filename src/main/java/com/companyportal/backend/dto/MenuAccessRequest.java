/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** Replaces one user type's or designation's checkpoints: numbers, or just "*". Empty = no menus. */
@Data
public class MenuAccessRequest {

    @NotNull
    @Size(max = 200)
    private List<@NotNull @Size(max = 10) String> checkpoints = new ArrayList<>();
}
