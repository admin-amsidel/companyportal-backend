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

/**
 * A skill a person lists on their own profile (Java, Spring Boot, AutoCAD…), with an optional note (experience,
 * projects, certificates). Kept by the person; HR and managers see it on the employee's record. Not the skills rated in
 * a performance review (review_skills).
 */
@Entity
@Table(name = "employee_skills",
        uniqueConstraints = @UniqueConstraint(name = "uk_employee_skill_name", columnNames = {"employee_id", "name"}),
        indexes = @Index(name = "ix_employee_skill_employee", columnList = "employee_id"))
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class EmployeeSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(length = 500)
    private String note;

    /** The order the person shows them in: the order added. */
    @Column(nullable = false)
    private int sortOrder;

    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
