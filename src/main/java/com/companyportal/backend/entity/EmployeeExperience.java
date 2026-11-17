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

/**
 * A job a person had before (or alongside) this one, on their own profile: the company, the role, from when to when
 * (months: the dates are the 1st of the month), and what they did. Kept by the person; HR and managers see it on the
 * employee's record.
 */
@Entity
@Table(name = "employee_experiences", indexes = @Index(name = "ix_employee_experience_employee", columnList = "employee_id"))
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class EmployeeExperience {

    public enum Type { FULL_TIME, PART_TIME, CONTRACT, INTERNSHIP, FREELANCE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(nullable = false, length = 150)
    private String companyName;

    @Column(nullable = false, length = 100)
    private String jobTitle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Type employmentType;

    @Column(length = 150)
    private String location;

    /** The first month worked (its 1st). */
    @Column(nullable = false)
    private LocalDate startMonth;

    /** The last month worked (its 1st); null = still working there. */
    private LocalDate endMonth;

    @Column(length = 2000)
    private String responsibilities;

    @Column(length = 1000)
    private String achievements;

    /** Tools and technologies used there. */
    @Column(length = 300)
    private String tools;

    @Column(length = 300)
    private String reasonForLeaving;

    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
