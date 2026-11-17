/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * One revision of an employee's salary in a financial year (April to March): the monthly amount of each pay component,
 * from effectiveFrom until the day before the year's next revision (or the year's end, or the person's exit date). A
 * year may have several (a raise in October). The year's income tax set-up is in PayrollTaxYear.
 */
@Entity
@Table(name = "salary_structures",
        uniqueConstraints = @UniqueConstraint(name = "uk_salary_employee_from", columnNames = {"employee_id", "effective_from"}))
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class SalaryStructure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    /** The year the financial year starts in: 2025 = FY 2025-26 (1 Apr 2025 to 31 Mar 2026). */
    @Column(name = "fy_start_year", nullable = false)
    private int fyStartYear;

    /** First day this salary is paid for, inside the financial year. */
    @Column(nullable = false)
    private LocalDate effectiveFrom;

    /** Working days in a month for this salary (default 30): per-day pay = monthly earnings / working days. */
    @Column(nullable = false, columnDefinition = "integer default 30")
    private int workingDays = 30;

    @Column(length = 500)
    private String notes;

    /** EPF is worked out: 12% of the Basic + DA paid, on at most the wage ceiling unless pfFullWage. */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean pfApplies;

    /** EPF on the whole Basic + DA, not capped at the wage ceiling. */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean pfFullWage;

    /** ESI is worked out (0.75% of the gross paid) while the monthly gross is within the ESI limit. */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean esiApplies;

    /** Professional tax is worked out from the slabs, per half year. */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean ptApplies;

    @OneToMany(mappedBy = "structure", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<SalaryLine> lines = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private Employee updatedBy;

    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
