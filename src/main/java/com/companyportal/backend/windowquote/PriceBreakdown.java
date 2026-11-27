/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.windowquote;

import java.util.List;

public record PriceBreakdown(
        List<BomLine> lines,
        double areaSqm,
        double materialCost,
        double wastage,
        double labour,
        double installation,
        double costPerUnit,
        double marginAmount,
        double unitPrice,
        int quantity,
        double lineTotal,
        List<String> warnings) {

    /** Bill-of-material / costing line. category: PROFILE, GLASS, PANEL, HARDWARE, ACCESSORY, WASTAGE, LABOUR */
    public record BomLine(String category, String description, double qty, String unit,
                          double rate, double amount) {}
}
