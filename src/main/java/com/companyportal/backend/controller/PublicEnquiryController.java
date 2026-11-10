/*
 * Copyright (c) 2026 AMSIDEL Technologies. All rights reserved.
 * Designed and developed by AMSIDEL Technologies.
 */
package com.companyportal.backend.controller;

import com.companyportal.backend.dto.EnquiryDtos.EnquiryReceived;
import com.companyportal.backend.dto.EnquiryDtos.EnquiryRequest;
import com.companyportal.backend.service.EnquiryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * The website's support chat leaves call-back and demo requests here: no sign-in (see SecurityConfig: only this POST
 * is open).
 */
@RestController
@RequestMapping("/api/public/enquiries")
@RequiredArgsConstructor
public class PublicEnquiryController {

    private final EnquiryService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EnquiryReceived create(@Valid @RequestBody EnquiryRequest request, HttpServletRequest http) {
        return service.receive(request, visitor(http));
    }

    /** Who is asking, for the per-visitor limit: the address the hosting's proxy saw, else the connection's. */
    private static String visitor(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] hops = forwarded.split(",");
            // the last hop is the one added by our own proxy; earlier ones are whatever the caller sent
            return hops[hops.length - 1].trim();
        }
        return http.getRemoteAddr();
    }
}
