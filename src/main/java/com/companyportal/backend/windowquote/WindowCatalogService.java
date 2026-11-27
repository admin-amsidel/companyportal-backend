/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.windowquote;

import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;

/**
 * The door and window quote catalogue (materials, profile series, colours, glass, hardware, rates and settings), read
 * from window-quote/catalog.json at start-up. All rates there are sample values until the real price list replaces them.
 */
@Service
public class WindowCatalogService {

    private static final JsonMapper JSON = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    private Catalog catalog;

    @PostConstruct
    void load() throws IOException {
        try (InputStream in = new ClassPathResource("window-quote/catalog.json").getInputStream()) {
            catalog = JSON.readValue(in, Catalog.class);
        }
    }

    public Catalog get() {
        return catalog;
    }

    /** The authoritative price of one design (the browser's price is only a preview). */
    public PriceBreakdown price(Design design) {
        return new PricingEngine(catalog).calculate(design);
    }
}
