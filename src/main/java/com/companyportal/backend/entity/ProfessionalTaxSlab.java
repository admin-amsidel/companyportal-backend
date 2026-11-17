/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Professional tax for a half year (April to September, October to March): a half year's gross pay from fromIncome up
 * (to the next slab) pays halfYearTax. Kerala's slabs to start with; payroll keeps them up to date.
 */
@Entity
@Table(name = "professional_tax_slabs", uniqueConstraints = @UniqueConstraint(name = "uk_pt_slab_from", columnNames = "from_income"))
@Getter
@Setter
@NoArgsConstructor
public class ProfessionalTaxSlab {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "from_income", nullable = false, precision = 14, scale = 2)
    private BigDecimal fromIncome;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal halfYearTax;
}
