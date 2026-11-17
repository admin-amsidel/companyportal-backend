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

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One person's income tax set-up for a financial year, shared by the year's salary revisions: the regime, the old
 * regime's declared deductions, and the income and tax already paid this year outside the portal (months paid before
 * the portal was used, or by a previous employer, as on Form 12B). Months with no payslip in the portal count nothing
 * else.
 */
@Entity
@Table(name = "payroll_tax_years",
        uniqueConstraints = @UniqueConstraint(name = "uk_tax_year_employee", columnNames = {"employee_id", "fy_start_year"}))
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class PayrollTaxYear {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    /** 2025 = FY 2025-26. */
    @Column(name = "fy_start_year", nullable = false)
    private int fyStartYear;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TaxRegime taxRegime = TaxRegime.MANUAL;

    /** Old regime: deductions and exemptions declared for the year (80C, 80D, HRA, home-loan interest...). */
    @Column(precision = 14, scale = 2)
    private BigDecimal taxDeclared;

    /** Gross salary paid this year outside the portal. */
    @Column(precision = 14, scale = 2)
    private BigDecimal outsideIncome;

    /** Income tax already deducted this year outside the portal. */
    @Column(precision = 14, scale = 2)
    private BigDecimal outsideTax;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private Employee updatedBy;

    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
