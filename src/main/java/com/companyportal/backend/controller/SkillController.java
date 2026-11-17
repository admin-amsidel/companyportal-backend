/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.controller;

import com.companyportal.backend.dto.SkillDtos.SkillBody;
import com.companyportal.backend.dto.SkillDtos.SkillView;
import com.companyportal.backend.security.AuthUser;
import com.companyportal.backend.security.Checkpoint;
import com.companyportal.backend.security.Menus;
import com.companyportal.backend.service.EmployeeSkillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Skills on a person's profile: their own (anyone signed in), and another person's on their record. */
@RestController
@RequiredArgsConstructor
public class SkillController {

    private final EmployeeSkillService service;

    @GetMapping("/api/me/skills")
    public List<SkillView> mine(@AuthenticationPrincipal AuthUser me) {
        return service.mine(me);
    }

    @PostMapping("/api/me/skills")
    @ResponseStatus(HttpStatus.CREATED)
    public SkillView add(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody SkillBody body) {
        return service.add(me, body);
    }

    @PutMapping("/api/me/skills/{id}")
    public SkillView update(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @Valid @RequestBody SkillBody body) {
        return service.update(me, id, body);
    }

    @DeleteMapping("/api/me/skills/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        service.delete(me, id);
    }

    /** Names to offer while typing: the company's own skills, then common ones. */
    @GetMapping("/api/skills/suggestions")
    public List<String> suggestions(@RequestParam(required = false) String q) {
        return service.suggestions(q);
    }

    /** Another person's skills, for whoever may see their record. */
    @Checkpoint({Menus.EMP_LIST})
    @GetMapping("/api/employees/{id}/skills")
    public List<SkillView> of(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return service.of(me, id);
    }
}
