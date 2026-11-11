/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.service;

import com.companyportal.backend.dto.TeamMember;
import com.companyportal.backend.entity.Employee;
import com.companyportal.backend.entity.Role;
import com.companyportal.backend.repository.EmployeeRepository;
import com.companyportal.backend.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Whose day-to-day records (work log, attendance) a person may look at: their own; everyone below them in the
 * reporting line; and, for owners and admins, everyone in their scope. Call inside a transaction (it walks the
 * reporting line).
 */
@Component
@RequiredArgsConstructor
public class TeamAccess {

    private final EmployeeRepository employeeRepository;

    /** Myself; owners; admins of the person's brand (every brand for group admins); anyone above them. */
    public boolean canView(AuthUser me, Employee person) {
        if (me.id().equals(person.getId()) || me.isOwner()) {
            return true;
        }
        Long brandId = person.getBrand() == null ? null : person.getBrand().getId();
        if (me.role() == Role.ADMIN && (me.brandId() == null || me.brandId().equals(brandId))) {
            return true;
        }
        Set<Long> seen = new HashSet<>();
        for (Employee up = person.getReportsTo(); up != null && seen.add(up.getId()); up = up.getReportsTo()) {
            if (up.getId().equals(me.id())) {
                return true;
            }
        }
        return false;
    }

    /** Active people I may look at, not counting me, by name. */
    public List<Employee> team(AuthUser me) {
        return employeeRepository.search(null).stream()
                .filter(e -> e.isActive() && !e.getId().equals(me.id()) && canView(me, e))
                .toList();
    }

    public List<TeamMember> members(AuthUser me) {
        return team(me).stream().map(TeamMember::from).toList();
    }
}
