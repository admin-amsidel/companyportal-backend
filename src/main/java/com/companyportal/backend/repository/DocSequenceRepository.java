/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.repository;

import com.companyportal.backend.entity.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/** Bill number counters, locked while a number is taken. */
public interface DocSequenceRepository extends JpaRepository<DocSequence, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from DocSequence s where s.prefix = :prefix")
    Optional<DocSequence> lock(@Param("prefix") String prefix);
}
