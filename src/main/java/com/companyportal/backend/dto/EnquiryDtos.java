/*
 * Copyright (c) 2026 AMSIDEL Technologies. All rights reserved.
 * Designed and developed by AMSIDEL Technologies.
 */
package com.companyportal.backend.dto;

import com.companyportal.backend.entity.EnquiryKind;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public final class EnquiryDtos {

    private EnquiryDtos() {
    }

    /**
     * From the website's support chat. {@code website} is a field people never see: only a robot fills it, and then
     * the request is quietly dropped.
     */
    public record EnquiryRequest(
            @NotNull EnquiryKind kind,
            @NotBlank @Size(min = 2, max = 80) String name,
            @NotBlank @Pattern(regexp = "\\+?[0-9][0-9 -]{6,18}", message = "Enter a valid phone number") String phone,
            @NotBlank @Email @Size(max = 120) String email,
            @Size(max = 120) String company,
            @Size(max = 500) String message,
            LocalDate demoDate,
            LocalTime demoTime,
            @Size(max = 200) String website) {
    }

    public record EnquiryReceived(Long id, EnquiryKind kind) {
    }
}
