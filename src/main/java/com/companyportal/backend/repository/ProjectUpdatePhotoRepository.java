/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.repository;

import com.companyportal.backend.entity.ProjectUpdatePhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProjectUpdatePhotoRepository extends JpaRepository<ProjectUpdatePhoto, Long> {

    /** A photo with the project it belongs to (to check who may see it). */
    @Query("select ph from ProjectUpdatePhoto ph join fetch ph.update u join fetch u.project p join fetch p.brand where ph.id = :id")
    Optional<ProjectUpdatePhoto> findFull(@Param("id") Long id);
}
