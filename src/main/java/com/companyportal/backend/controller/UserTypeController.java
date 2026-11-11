/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.controller;

import com.companyportal.backend.service.UserTypeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** User types (portal access): read by everyone signed in; added, changed and deleted by owners and admins (SecurityConfig). */
@RestController
@RequestMapping("/api/user-types")
@RequiredArgsConstructor
public class UserTypeController {

    private final UserTypeService service;

    @GetMapping
    public List<UserTypeService.View> list() {
        return service.list();
    }

    @com.companyportal.backend.security.Checkpoint({com.companyportal.backend.security.Menus.USER_TYPES})
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserTypeService.View create(@org.springframework.security.core.annotation.AuthenticationPrincipal com.companyportal.backend.security.AuthUser me,
                                       @Valid @RequestBody UserTypeService.Body body) {
        return service.create(me, body);
    }

    /** Only one nobody has. */
    @com.companyportal.backend.security.Checkpoint({com.companyportal.backend.security.Menus.USER_TYPES})
    @PutMapping("/{id}")
    public UserTypeService.View update(@org.springframework.security.core.annotation.AuthenticationPrincipal com.companyportal.backend.security.AuthUser me,
                                       @PathVariable Long id, @Valid @RequestBody UserTypeService.Body body) {
        return service.update(me, id, body);
    }

    /** Only one nobody has, and not a built-in one. */
    @com.companyportal.backend.security.Checkpoint({com.companyportal.backend.security.Menus.USER_TYPES})
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@org.springframework.security.core.annotation.AuthenticationPrincipal com.companyportal.backend.security.AuthUser me, @PathVariable Long id) {
        service.delete(me, id);
    }
}
