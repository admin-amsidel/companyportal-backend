/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * What a Manual Sales worklog task is about, typed by hand. Kept as text and apart from the customers, projects and agents
 * of the portals: this flow is for brands without the product portal, so nothing here points at their records.
 */
@Entity
@Table(name = "manual_sales_tasks")
@Getter
@Setter
@NoArgsConstructor
public class ManualSalesTask {

    /** Same value as tasks.id. */
    @Id
    @Column(name = "task_id")
    private Long taskId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "task_id")
    private WorkTask task;

    @Column(length = 150)
    private String customerName;

    @Column(length = 30)
    private String customerPhone;

    @Column(length = 150)
    private String projectName;

    @Column(length = 150)
    private String agentName;

    @Column(length = 30)
    private String agentPhone;

    /** The project it is for; its customer, name and agent fill the fields above (kept if the project goes). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    /** The Team performance goal it was made for; its person is the task's one assignee. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_id")
    private Goal goal;
}
