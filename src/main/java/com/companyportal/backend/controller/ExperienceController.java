/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.controller;

import com.companyportal.backend.dto.ExperienceDtos.Experience;
import com.companyportal.backend.dto.ExperienceDtos.ExperienceBody;
import com.companyportal.backend.dto.ExperienceDtos.ExperienceView;
import com.companyportal.backend.security.AuthUser;
import com.companyportal.backend.security.Checkpoint;
import com.companyportal.backend.security.Menus;
import com.companyportal.backend.service.EmployeeExperienceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Work experience on a person's profile: their own (anyone signed in), and another person's on their record. */
@RestController
@RequiredArgsConstructor
public class ExperienceController {

    private final EmployeeExperienceService service;

    @GetMapping("/api/me/experience")
    public Experience mine(@AuthenticationPrincipal AuthUser me) {
        return service.mine(me);
    }

    @PostMapping("/api/me/experience")
    @ResponseStatus(HttpStatus.CREATED)
    public ExperienceView add(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody ExperienceBody body) {
        return service.add(me, body);
    }

    @PutMapping("/api/me/experience/{id}")
    public ExperienceView update(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @Valid @RequestBody ExperienceBody body) {
        return service.update(me, id, body);
    }

    @DeleteMapping("/api/me/experience/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        service.delete(me, id);
    }

    /** Another person's experience, for whoever may see their record. */
    @Checkpoint({Menus.EMP_LIST})
    @GetMapping("/api/employees/{id}/experience")
    public Experience of(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return service.of(me, id);
    }
}
