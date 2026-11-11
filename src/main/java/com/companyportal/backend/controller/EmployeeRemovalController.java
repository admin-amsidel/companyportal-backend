/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.controller;

import com.companyportal.backend.security.AuthUser;
import com.companyportal.backend.security.Checkpoint;
import com.companyportal.backend.security.Menus;
import com.companyportal.backend.service.EmployeeRemovalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Deleting employees: its own tick. Apart from /api/employees, whose changes are for owners and admins whatever the ticks. */
@RestController
@RequestMapping("/api/employee-removal")
@RequiredArgsConstructor
public class EmployeeRemovalController {

    private final EmployeeRemovalService service;

    @Checkpoint({Menus.EMP_DELETE})
    @PostMapping
    public EmployeeRemovalService.DeleteResult delete(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody EmployeeRemovalService.DeleteBody body) {
        return service.delete(me, body.ids());
    }
}
