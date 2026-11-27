/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.windowquote;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * One line of a quotation, in its order:
 * <ul>
 *   <li>DESIGN: a door, window or frame from the configurator, priced by its costing breakdown (COSTING, which may be
 *   edited: costLines and marginPct) or by its area at a rate per sq ft (SQFT);</li>
 *   <li>ITEM: a particular typed in (title, bullet description, optional size), counted in sq ft, numbers, running feet
 *   or a quantity, or a lump sum;</li>
 *   <li>SECTION: a heading such as "Ground floor".</li>
 * </ul>
 */
public record QuoteLine(
        @NotNull Kind kind,
        /** ITEM: the item name; SECTION: the heading. A DESIGN line uses its design's label. */
        @Size(max = 300) String title,
        /** ITEM: one point per line; DESIGN: profile details, one per line. */
        @Size(max = 3000) String description,
        // ---- ITEM
        @Size(max = 60) String size,
        Double nos,
        Unit unit,
        /** null = lump sum (the rate is the total). */
        Double qty,
        Double rate,
        // ---- DESIGN
        @Valid Design design,
        Pricing pricing,
        Double ratePerSqft,
        /** The costing breakdown as edited by hand; null = as calculated. */
        @Size(max = 60) List<PriceBreakdown.BomLine> costLines,
        /** The margin for this line; null = the catalogue's. */
        Double marginPct,
        @Size(max = 20) String code,
        @Size(max = 100) String location,
        @Size(max = 1500) String accessories,
        @Size(max = 300) String remarks) {

    public enum Kind { DESIGN, ITEM, SECTION }

    public enum Unit { SQFT, NOS, RFT, QTY, LUMPSUM }

    public enum Pricing { COSTING, SQFT }

    /** What the server worked out for a line: how it is counted, the rate, the total, and (DESIGN) the costing. */
    public record Result(Unit unit, Double qty, double rate, double unitPrice, double total, Double areaSqft, PriceBreakdown price) {
    }

    /** A line with its result, as a quotation keeps it. */
    public record Priced(QuoteLine line, Result result) {
    }
}
