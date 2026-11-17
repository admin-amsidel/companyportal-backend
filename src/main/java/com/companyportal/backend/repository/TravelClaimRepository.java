/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.repository;

import com.companyportal.backend.entity.TravelClaim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TravelClaimRepository extends JpaRepository<TravelClaim, Long> {

    String FULL = "select distinct c from TravelClaim c join fetch c.employee e left join fetch e.brand left join fetch e.approver"
            + " left join fetch e.reportsTo left join fetch c.decidedBy left join fetch c.settledBy left join fetch c.customer ";

    @Query(FULL + "where c.id = :id")
    Optional<TravelClaim> findFull(@Param("id") Long id);

    /** These people's claims with a meeting date in the range, newest first. */
    @Query(FULL + "where e.id in :employeeIds and c.meetingDate between :from and :to order by c.meetingDate desc, c.id desc")
    List<TravelClaim> forPeople(@Param("employeeIds") Collection<Long> employeeIds, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** A GPS trip of this person not yet sent: on the way or waiting for its distance to be confirmed. */
    @Query(FULL + "where e.id = :employeeId and c.status in (com.companyportal.backend.entity.TravelClaim.Status.IN_PROGRESS,"
            + " com.companyportal.backend.entity.TravelClaim.Status.TO_CONFIRM)")
    List<TravelClaim> openTrips(@Param("employeeId") Long employeeId);

    /** One person's claims sent for approval or decided (approved, waiting, settled) on a meeting day: what a new claim may repeat. */
    @Query("select c from TravelClaim c where c.employee.id = :employeeId and c.meetingDate = :day and c.status in"
            + " (com.companyportal.backend.entity.TravelClaim.Status.SUBMITTED, com.companyportal.backend.entity.TravelClaim.Status.APPROVED,"
            + " com.companyportal.backend.entity.TravelClaim.Status.WAITING_SETTLEMENT, com.companyportal.backend.entity.TravelClaim.Status.SETTLED)"
            + " order by c.id")
    List<TravelClaim> sentOn(@Param("employeeId") Long employeeId, @Param("day") LocalDate day);
}
