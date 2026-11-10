/*
 * Copyright (c) 2026 AMSIDEL Technologies. All rights reserved.
 * Designed and developed by AMSIDEL Technologies.
 */
package com.companyportal.backend.controller;

import com.companyportal.backend.security.AuthUser;
import com.companyportal.backend.security.Checkpoint;
import com.companyportal.backend.security.Menus;
import com.companyportal.backend.service.EnquiryAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Administration > Website enquiries: the list, and the sales team's status and notes. Its own tick. */
@RestController
@RequestMapping("/api/enquiries")
@Checkpoint({Menus.ENQUIRIES})
@RequiredArgsConstructor
public class EnquiryController {

    private final EnquiryAdminService service;

    @GetMapping
    public List<EnquiryAdminService.View> list() {
        return service.list();
    }

    @PutMapping("/{id}")
    public EnquiryAdminService.View update(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                           @Valid @RequestBody EnquiryAdminService.FollowUp body) {
        return service.update(me, id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
