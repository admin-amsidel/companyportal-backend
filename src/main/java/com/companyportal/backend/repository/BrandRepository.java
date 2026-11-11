/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.repository;

import com.companyportal.backend.entity.Brand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BrandRepository extends JpaRepository<Brand, Long> {

    List<Brand> findAllByOrderByNameAsc();

    boolean existsByCodeIgnoreCase(String code);

    @Query("select b.colorPalette from Brand b where b.id = :id")
    String colorPaletteOf(@Param("id") Long id);

    @Query("select b.timeZone from Brand b where b.id = :id")
    String timeZoneOf(@Param("id") Long id);
}
