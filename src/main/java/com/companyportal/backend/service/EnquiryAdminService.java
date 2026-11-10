/*
 * Copyright (c) 2026 AMSIDEL Technologies. All rights reserved.
 * Designed and developed by AMSIDEL Technologies.
 */
package com.companyportal.backend.service;

import com.companyportal.backend.entity.EnquiryKind;
import com.companyportal.backend.entity.EnquiryStatus;
import com.companyportal.backend.entity.SalesEnquiry;
import com.companyportal.backend.exception.ResourceNotFoundException;
import com.companyportal.backend.repository.EmployeeRepository;
import com.companyportal.backend.repository.SalesEnquiryRepository;
import com.companyportal.backend.security.AuthUser;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Administration > Website enquiries: the sales team follows up the call-back and demo requests from the website chat
 * (status and notes). Only for those given the menu; owners and admins have it through "*".
 */
@Service
@RequiredArgsConstructor
public class EnquiryAdminService {

    private final SalesEnquiryRepository repository;
    private final EmployeeRepository employees;
    private final Clock clock;

    public record View(Long id, EnquiryKind kind, String name, String phone, String email, String company,
                       String message, LocalDate demoDate, LocalTime demoTime, boolean notified, Instant createdAt,
                       EnquiryStatus status, String notes, String updatedByName, Instant updatedAt) {

        static View of(SalesEnquiry e) {
            return new View(e.getId(), e.getKind(), e.getName(), e.getPhone(), e.getEmail(), e.getCompany(),
                    e.getMessage(), e.getDemoDate(), e.getDemoTime(), e.isNotified(), e.getCreatedAt(),
                    e.getStatus(), e.getNotes(), e.getUpdatedByName(), e.getUpdatedAt());
        }
    }

    public record FollowUp(@NotNull EnquiryStatus status, @Size(max = 1000) String notes) {
    }

    @Transactional(readOnly = true)
    public List<View> list() {
        return repository.newestFirst().stream().map(View::of).toList();
    }

    @Transactional
    public View update(AuthUser me, Long id, FollowUp body) {
        SalesEnquiry e = find(id);
        e.setStatus(body.status());
        e.setNotes(StringUtils.hasText(body.notes()) ? body.notes().trim() : null);
        e.setUpdatedByName(employees.findById(me.id()).map(x -> x.getName()).orElse(me.userId()));
        e.setUpdatedAt(clock.instant());
        return View.of(e);
    }

    /** Spam or a duplicate: gone for good. */
    @Transactional
    public void delete(Long id) {
        repository.delete(find(id));
    }

    private SalesEnquiry find(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Enquiry not found"));
    }
}
