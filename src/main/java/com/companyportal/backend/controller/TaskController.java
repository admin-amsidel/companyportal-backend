/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.controller;

import com.companyportal.backend.dto.MyTask;
import com.companyportal.backend.dto.TaskRequest;
import com.companyportal.backend.dto.TaskResponse;
import com.companyportal.backend.entity.TaskStatus;
import com.companyportal.backend.security.AuthUser;
import com.companyportal.backend.security.Checkpoint;
import com.companyportal.backend.security.Menus;
import com.companyportal.backend.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Tasks: /mine with My work log (to log hours); creating and assigning needs the Tasks menu. */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@Checkpoint({Menus.TASKS})
public class TaskController {

    private final TaskService taskService;

    @Checkpoint({Menus.WORKLOG_MY})
    @GetMapping("/mine")
    public List<MyTask> mine(@AuthenticationPrincipal AuthUser me) {
        return taskService.mine(me);
    }

    @GetMapping
    public List<TaskResponse> list(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) TaskStatus status) {
        return taskService.list(me, status);
    }

    /** The customers a task may be for (the Customer ID picker); no Customers menu needed. */
    @GetMapping("/customers")
    public List<TaskResponse.CustomerOption> customers(@AuthenticationPrincipal AuthUser me) {
        return taskService.customers(me);
    }

    /** The task's page: the task and every work log entry against it (who, day, hours, what was done). */
    @GetMapping("/{id}/logs")
    public TaskResponse.TaskLogs logs(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return taskService.logs(me, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody TaskRequest request) {
        return taskService.create(me, request);
    }

    /** What deleting the task would remove (for the confirmation). Owners and admins only. */
    @GetMapping("/{id}/delete-check")
    public TaskService.DeleteCheck deleteCheck(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return taskService.deleteCheck(me, id);
    }

    /** Deletes the task with its work log entries and goal items (test data). Owners and admins only. */
    @DeleteMapping("/{id}")
    public TaskService.DeleteCheck delete(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return taskService.delete(me, id);
    }

    @PutMapping("/{id}")
    public TaskResponse update(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                               @Valid @RequestBody TaskRequest request) {
        return taskService.update(me, id, request);
    }
}
