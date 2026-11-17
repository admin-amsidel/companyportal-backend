/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.service;

import com.companyportal.backend.dto.SkillDtos.SkillBody;
import com.companyportal.backend.dto.SkillDtos.SkillView;
import com.companyportal.backend.entity.EmployeeSkill;
import com.companyportal.backend.exception.ApiException;
import com.companyportal.backend.exception.ResourceNotFoundException;
import com.companyportal.backend.repository.EmployeeRepository;
import com.companyportal.backend.repository.EmployeeSkillRepository;
import com.companyportal.backend.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The skills a person lists on their own profile: they add, change and remove their own; whoever may see the employee's
 * record sees them there. Names are kept as typed (trimmed), and the same name twice (any case) is refused.
 */
@Service
@RequiredArgsConstructor
public class EmployeeSkillService {

    static final int MAX_SKILLS = 50;

    /** Common skills offered while typing, before the company has its own. */
    static final List<String> COMMON = List.of(
            "Java", "Spring Boot", "Angular", "TypeScript", "JavaScript", "React", "Node.js", "Python", "SQL", "PostgreSQL",
            "HTML & CSS", "Git", "Docker", "AWS", "Azure", "REST APIs", "Microservices", "Testing", "Agile / Scrum",
            "Project management", "Team leadership", "Customer handling", "Sales", "Negotiation", "Marketing",
            "Digital marketing", "Communication", "Presentation", "MS Excel", "MS Office", "Tally", "Accounting", "GST filing",
            "AutoCAD", "SketchUp", "3D design", "Site supervision", "Installation", "Measurement", "Quality control",
            "uPVC fabrication", "Aluminium fabrication", "Glass work", "Interior design", "Procurement", "Inventory",
            "Logistics", "Driving (LMV)", "English", "Hindi", "Malayalam", "Tamil");

    private final EmployeeSkillRepository skills;
    private final EmployeeRepository employees;
    private final EmployeeService employeeService;

    @Transactional(readOnly = true)
    public List<SkillView> mine(AuthUser me) {
        return skills.ofEmployee(me.id()).stream().map(SkillView::of).toList();
    }

    /** Someone else's, for whoever may see their record. */
    @Transactional(readOnly = true)
    public List<SkillView> of(AuthUser me, Long employeeId) {
        employeeService.get(me, employeeId);
        return skills.ofEmployee(employeeId).stream().map(SkillView::of).toList();
    }

    @Transactional
    public SkillView add(AuthUser me, SkillBody body) {
        List<EmployeeSkill> current = skills.ofEmployee(me.id());
        if (current.size() >= MAX_SKILLS) {
            throw ApiException.badRequest("You can list at most " + MAX_SKILLS + " skills");
        }
        String name = name(body.name());
        refuseTwice(current, name, null);
        EmployeeSkill s = new EmployeeSkill();
        s.setEmployee(employees.getReferenceById(me.id()));
        s.setName(name);
        s.setNote(note(body.note()));
        s.setSortOrder(current.stream().mapToInt(EmployeeSkill::getSortOrder).max().orElse(0) + 10);
        return SkillView.of(skills.save(s));
    }

    @Transactional
    public SkillView update(AuthUser me, Long id, SkillBody body) {
        EmployeeSkill s = own(me, id);
        String name = name(body.name());
        refuseTwice(skills.ofEmployee(me.id()), name, id);
        s.setName(name);
        s.setNote(note(body.note()));
        return SkillView.of(s);
    }

    @Transactional
    public void delete(AuthUser me, Long id) {
        skills.delete(own(me, id));
    }

    /** What to offer while typing: the company's own skills first (most listed), then common ones; up to 20 that match. */
    @Transactional(readOnly = true)
    public List<String> suggestions(String query) {
        String q = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        Set<String> seen = new LinkedHashSet<>();
        Set<String> keys = new java.util.HashSet<>();
        for (String n : concat(skills.namesInUse(), COMMON)) {
            if ((q.isEmpty() || n.toLowerCase(Locale.ROOT).contains(q)) && keys.add(n.toLowerCase(Locale.ROOT))) {
                seen.add(n);
            }
            if (seen.size() == 20) {
                break;
            }
        }
        return List.copyOf(seen);
    }

    private EmployeeSkill own(AuthUser me, Long id) {
        EmployeeSkill s = skills.findById(id).orElseThrow(() -> new ResourceNotFoundException("Skill not found: " + id));
        if (!s.getEmployee().getId().equals(me.id())) {
            throw ApiException.forbidden("You can only change your own skills");
        }
        return s;
    }

    private static void refuseTwice(List<EmployeeSkill> current, String name, Long except) {
        current.stream().filter(s -> !s.getId().equals(except) && s.getName().equalsIgnoreCase(name)).findAny().ifPresent(s -> {
            throw ApiException.conflict("You have already listed " + s.getName());
        });
    }

    /** Spaces trimmed and runs of spaces made one. */
    private static String name(String raw) {
        String n = raw == null ? "" : raw.strip().replaceAll("\\s+", " ");
        if (n.isEmpty()) {
            throw ApiException.badRequest("Give the skill, e.g. Java");
        }
        return n;
    }

    private static String note(String raw) {
        return raw == null || raw.isBlank() ? null : raw.strip();
    }

    private static List<String> concat(List<String> a, List<String> b) {
        List<String> out = new java.util.ArrayList<>(a);
        out.addAll(b);
        return out;
    }
}
