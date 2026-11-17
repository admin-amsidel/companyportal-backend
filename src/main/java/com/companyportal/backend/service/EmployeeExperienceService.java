/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.service;

import com.companyportal.backend.dto.ExperienceDtos.Experience;
import com.companyportal.backend.dto.ExperienceDtos.ExperienceBody;
import com.companyportal.backend.dto.ExperienceDtos.ExperienceView;
import com.companyportal.backend.entity.Employee;
import com.companyportal.backend.entity.EmployeeExperience;
import com.companyportal.backend.exception.ApiException;
import com.companyportal.backend.exception.ResourceNotFoundException;
import com.companyportal.backend.repository.EmployeeExperienceRepository;
import com.companyportal.backend.repository.EmployeeRepository;
import com.companyportal.backend.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The jobs a person lists on their own profile (their experience): they add, change and remove their own; whoever may
 * see the employee's record sees them there. Totals: the months before this company (jobs that overlap counted once,
 * up to the month before joining) and the months here (from the joining date).
 */
@Service
@RequiredArgsConstructor
public class EmployeeExperienceService {

    static final int MAX_JOBS = 30;

    private final EmployeeExperienceRepository jobs;
    private final EmployeeRepository employees;
    private final EmployeeService employeeService;

    @Value("${app.leave.zone:Asia/Kolkata}")
    private String zone;

    @Transactional(readOnly = true)
    public Experience mine(AuthUser me) {
        return experience(employees.findById(me.id()).orElseThrow(), jobs.ofEmployee(me.id()));
    }

    /** Someone else's, for whoever may see their record. */
    @Transactional(readOnly = true)
    public Experience of(AuthUser me, Long employeeId) {
        employeeService.get(me, employeeId);
        return experience(employees.findById(employeeId).orElseThrow(), jobs.ofEmployee(employeeId));
    }

    @Transactional
    public ExperienceView add(AuthUser me, ExperienceBody body) {
        if (jobs.ofEmployee(me.id()).size() >= MAX_JOBS) {
            throw ApiException.badRequest("You can list at most " + MAX_JOBS + " jobs");
        }
        EmployeeExperience x = new EmployeeExperience();
        x.setEmployee(employees.getReferenceById(me.id()));
        fill(x, body);
        return view(jobs.save(x));
    }

    @Transactional
    public ExperienceView update(AuthUser me, Long id, ExperienceBody body) {
        EmployeeExperience x = own(me, id);
        fill(x, body);
        return view(x);
    }

    @Transactional
    public void delete(AuthUser me, Long id) {
        jobs.delete(own(me, id));
    }

    // ---- rules ----

    private void fill(EmployeeExperience x, ExperienceBody body) {
        YearMonth now = YearMonth.now(ZoneId.of(zone));
        YearMonth start = YearMonth.from(body.startMonth());
        YearMonth end = body.endMonth() == null ? null : YearMonth.from(body.endMonth());
        if (start.isAfter(now)) {
            throw ApiException.badRequest("The start month cannot be in the future");
        }
        if (end != null && end.isAfter(now)) {
            throw ApiException.badRequest("The end month cannot be in the future; tick \"I still work here\" for a job you are in");
        }
        if (end != null && end.isBefore(start)) {
            throw ApiException.badRequest("The end month is before the start month");
        }
        x.setCompanyName(clean(body.companyName()));
        x.setJobTitle(clean(body.jobTitle()));
        x.setEmploymentType(body.employmentType());
        x.setLocation(clean(body.location()));
        x.setStartMonth(start.atDay(1));
        x.setEndMonth(end == null ? null : end.atDay(1));
        x.setResponsibilities(clean(body.responsibilities()));
        x.setAchievements(clean(body.achievements()));
        x.setTools(clean(body.tools()));
        x.setReasonForLeaving(clean(body.reasonForLeaving()));
    }

    private Experience experience(Employee e, List<EmployeeExperience> list) {
        YearMonth now = YearMonth.now(ZoneId.of(zone));
        LocalDate today = LocalDate.now(ZoneId.of(zone));
        YearMonth joined = e.getJoinedDate() == null ? null : YearMonth.from(e.getJoinedDate());
        // before this company: each job up to the month before joining, overlaps counted once
        List<YearMonth[]> spans = new ArrayList<>();
        for (EmployeeExperience x : list) {
            YearMonth s = YearMonth.from(x.getStartMonth());
            YearMonth t = x.getEndMonth() == null ? now : YearMonth.from(x.getEndMonth());
            if (joined != null && !t.isBefore(joined)) {
                t = joined.minusMonths(1);
            }
            if (!t.isBefore(s)) {
                spans.add(new YearMonth[]{s, t});
            }
        }
        spans.sort(Comparator.comparing((YearMonth[] a) -> a[0]));
        int before = 0;
        YearMonth[] open = null;
        for (YearMonth[] span : spans) {
            if (open == null || span[0].isAfter(open[1].plusMonths(1))) {
                if (open != null) {
                    before += months(open[0], open[1]);
                }
                open = new YearMonth[]{span[0], span[1]};
            } else if (span[1].isAfter(open[1])) {
                open[1] = span[1];
            }
        }
        if (open != null) {
            before += months(open[0], open[1]);
        }
        int here = e.getJoinedDate() == null || e.getJoinedDate().isAfter(today) ? 0
                : (int) Period.between(e.getJoinedDate(), e.getExitDate() != null && e.getExitDate().isBefore(today) ? e.getExitDate() : today).toTotalMonths();
        return new Experience(list.stream().map(this::view).toList(), before, here, before + here);
    }

    private ExperienceView view(EmployeeExperience x) {
        YearMonth start = YearMonth.from(x.getStartMonth());
        YearMonth end = x.getEndMonth() == null ? YearMonth.now(ZoneId.of(zone)) : YearMonth.from(x.getEndMonth());
        return new ExperienceView(x.getId(), x.getCompanyName(), x.getJobTitle(), x.getEmploymentType(), x.getLocation(),
                x.getStartMonth(), x.getEndMonth(), x.getEndMonth() == null, months(start, end), x.getResponsibilities(),
                x.getAchievements(), x.getTools(), x.getReasonForLeaving());
    }

    /** Both months counted: January to March = 3. */
    static int months(YearMonth from, YearMonth to) {
        return to.isBefore(from) ? 0 : (to.getYear() - from.getYear()) * 12 + to.getMonthValue() - from.getMonthValue() + 1;
    }

    private EmployeeExperience own(AuthUser me, Long id) {
        EmployeeExperience x = jobs.findById(id).orElseThrow(() -> new ResourceNotFoundException("Experience not found: " + id));
        if (!x.getEmployee().getId().equals(me.id())) {
            throw ApiException.forbidden("You can only change your own experience");
        }
        return x;
    }

    private static String clean(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
