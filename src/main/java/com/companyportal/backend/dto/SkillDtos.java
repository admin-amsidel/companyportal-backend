/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.dto;

import com.companyportal.backend.entity.EmployeeSkill;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The skills a person lists on their profile. */
public final class SkillDtos {

    private SkillDtos() {
    }

    public record SkillBody(
            @NotBlank(message = "Give the skill, e.g. Java") @Size(max = 60, message = "A skill is at most 60 characters") String name,
            @Size(max = 500, message = "The note is at most 500 characters") String note) {
    }

    public record SkillView(Long id, String name, String note) {
        public static SkillView of(EmployeeSkill s) {
            return new SkillView(s.getId(), s.getName(), s.getNote());
        }
    }
}
