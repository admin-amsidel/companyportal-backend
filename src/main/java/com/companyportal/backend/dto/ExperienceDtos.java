/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.dto;

import com.companyportal.backend.entity.EmployeeExperience;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** The jobs a person lists on their profile. Dates are months: any day of the month may be sent; the 1st is kept. */
public final class ExperienceDtos {

    private ExperienceDtos() {
    }

    public record ExperienceBody(
            @NotBlank(message = "Give the company's name") @Size(max = 150, message = "The company's name is at most 150 characters") String companyName,
            @NotBlank(message = "Give the job title") @Size(max = 100, message = "The job title is at most 100 characters") String jobTitle,
            @NotNull(message = "Choose the type of employment") EmployeeExperience.Type employmentType,
            @Size(max = 150, message = "The location is at most 150 characters") String location,
            @NotNull(message = "Give the month you started") LocalDate startMonth,
            /** Empty = still working there. */
            LocalDate endMonth,
            @Size(max = 2000, message = "Responsibilities: at most 2000 characters") String responsibilities,
            @Size(max = 1000, message = "Achievements: at most 1000 characters") String achievements,
            @Size(max = 300, message = "Tools and technologies: at most 300 characters") String tools,
            @Size(max = 300, message = "The reason for leaving is at most 300 characters") String reasonForLeaving) {
    }

    /** months: how long, both months counted (a job from January to March = 3). */
    public record ExperienceView(Long id, String companyName, String jobTitle, EmployeeExperience.Type employmentType, String location,
                                 LocalDate startMonth, LocalDate endMonth, boolean current, int months, String responsibilities,
                                 String achievements, String tools, String reasonForLeaving) {
    }

    /**
     * A person's experience: the jobs, latest first; the months of experience before this company (overlapping jobs
     * counted once) and here (from the joining date), and the two together.
     */
    public record Experience(List<ExperienceView> jobs, int monthsBefore, int monthsHere, int monthsTotal) {
    }
}
