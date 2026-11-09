/*
 * Copyright (c) 2026 AMSIDEL Technologies. All rights reserved.
 * Designed and developed by AMSIDEL Technologies.
 */
package com.companyportal.backend.dto;

import com.companyportal.backend.entity.WorkLog;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** A work log entry as the web app shows it; sequence = its order within the day. */
public record WorkLogEntry(
        Long id,
        LocalDate workDate,
        int sequence,
        String taskId,
        /** Title of the task; null for entries logged before tasks were managed in the portal. */
        String taskTitle,
        BigDecimal hours,
        String description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        /** With the approver; only approved hours count. */
        com.companyportal.backend.entity.ApprovalStatus approval,
        String decisionNote,
        /** On a Manual Sales worklog task; null on any other. */
        ManualSalesDtos.EntryView manualSales) {

    /** Call inside a transaction: reads the task. */
    public static WorkLogEntry from(WorkLog w) {
        return from(w, null);
    }

    /** Call inside a transaction: reads the task. */
    public static WorkLogEntry from(WorkLog w, ManualSalesDtos.EntryView manualSales) {
        return new WorkLogEntry(w.getId(), w.getWorkDate(), w.getSequence(), w.getTaskId(),
                w.getTask() == null ? null : w.getTask().getTitle(), w.getHours(),
                w.getDescription(), w.getCreatedAt(), w.getUpdatedAt(),
                w.getApproval() == null ? com.companyportal.backend.entity.ApprovalStatus.APPROVED : w.getApproval(), w.getDecisionNote(), manualSales);
    }
}
