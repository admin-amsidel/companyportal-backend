/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.controller;

import com.companyportal.backend.dto.WorkApprovalDtos.Decided;
import com.companyportal.backend.dto.WorkApprovalDtos.Decision;
import com.companyportal.backend.dto.WorkApprovalDtos.Queue;
import com.companyportal.backend.security.AuthUser;
import com.companyportal.backend.security.Checkpoint;
import com.companyportal.backend.security.Menus;
import com.companyportal.backend.service.WorkApprovalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** The approver's queue: attendance and work log entries waiting for them. */
@RestController
@RequestMapping("/api/work-approvals")
@Checkpoint({Menus.WORK_APPROVALS})
@RequiredArgsConstructor
public class WorkApprovalController {

    private final WorkApprovalService service;

    @GetMapping
    public Queue queue(@AuthenticationPrincipal AuthUser me) {
        return service.queue(me);
    }

    @PostMapping("/decide")
    public Decided decide(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody Decision decision) {
        return service.decide(me, decision);
    }
}
