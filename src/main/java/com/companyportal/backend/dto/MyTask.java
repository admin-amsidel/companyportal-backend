/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.dto;

import com.companyportal.backend.entity.WorkTask;

/** An open task assigned to the signed-in person, for the work log's Task ID picker. */
public record MyTask(Long id, String code, String title, String description, boolean manualSales, String currencySymbol,
                     String customerName, String projectName, String agentName) {

    public static MyTask from(WorkTask t) {
        return new MyTask(t.getId(), t.getCode(), t.getTitle(), t.getDescription(), false, null, null, null, null);
    }

    /** A Manual Sales worklog task: an entry on it asks for the status and the sales details. */
    public static MyTask manual(WorkTask t, String currencySymbol, String customerName, String projectName, String agentName) {
        return new MyTask(t.getId(), t.getCode(), t.getTitle(), t.getDescription(), true, currencySymbol, customerName, projectName, agentName);
    }
}
