/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.repository;

import com.companyportal.backend.entity.WindowQuote;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Door and window quotations. */
public interface WindowQuoteRepository extends JpaRepository<WindowQuote, Long> {

    @EntityGraph(attributePaths = {"brand", "createdBy"})
    List<WindowQuote> findAllByOrderByIdDesc();

    @EntityGraph(attributePaths = {"brand", "createdBy"})
    List<WindowQuote> findByBrandIdOrderByIdDesc(Long brandId);
}
