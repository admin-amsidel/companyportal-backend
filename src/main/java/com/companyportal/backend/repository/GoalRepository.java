/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.repository;

import com.companyportal.backend.entity.Goal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GoalRepository extends JpaRepository<Goal, Long> {

    @Query("select distinct g from Goal g left join fetch g.tasks left join fetch g.managerRatedBy"
            + " where g.review.id = :reviewId order by g.startDate, g.id")
    List<Goal> forReview(@Param("reviewId") Long reviewId);

    /** A person's goals in review cycles still open, newest cycle first (not the Task worklog goal: it gathers tasks itself). */
    @Query("select g from Goal g join fetch g.review r join fetch r.cycle c where r.employee.id = :employeeId and c.closed = false"
            + " and g.type <> com.companyportal.backend.entity.GoalType.TASK_WORKLOG order by c.startDate desc, g.id")
    List<Goal> openFor(@Param("employeeId") Long employeeId);

    @Query("select distinct g from Goal g left join fetch g.tasks where g.review.id in :reviewIds")
    List<Goal> forReviews(@Param("reviewIds") Collection<Long> reviewIds);

    @Query("select g from Goal g join fetch g.review r join fetch r.cycle join fetch r.employee e left join fetch e.brand"
            + " left join fetch e.reportsTo where g.id = :id")
    Optional<Goal> findFull(@Param("id") Long id);

    @Query("select coalesce(sum(g.weightage), 0) from Goal g where g.review.id = :reviewId and (:exceptId is null or g.id <> :exceptId)")
    int weightOf(@Param("reviewId") Long reviewId, @Param("exceptId") Long exceptId);

    /** These people's task worklog goals in cycles still open. */
    @Query("select distinct g from Goal g join fetch g.review r join fetch r.cycle c join fetch r.employee left join fetch g.tasks"
            + " where g.type = com.companyportal.backend.entity.GoalType.TASK_WORKLOG and c.closed = false and r.employee.id in :employeeIds")
    List<Goal> openWorklogGoals(@Param("employeeIds") Collection<Long> employeeIds);

    /** The goals (any cycle, open or closed) with a sub-task for this work log task. */
    @Query("select distinct g from Goal g join g.tasks t where t.workTask.id = :workTaskId")
    List<Goal> withWorkTask(@Param("workTaskId") Long workTaskId);

    /** The goal a sales achievement record belongs to. */
    @Query("select e.goal.id from SalesEntry e where e.id = :entryId")
    Optional<Long> goalOfSalesEntry(@Param("entryId") Long entryId);
}
