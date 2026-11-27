/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.windowquote;

import com.companyportal.backend.windowquote.Catalog.*;
import com.companyportal.backend.windowquote.PriceBreakdown.BomLine;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QuoteMathTest {

    private final Catalog catalog = new Catalog("INR",
            new Settings(8, 25, 18, 350, 250, 15),
            List.of(new MaterialDef("UPVC", "uPVC", 600, 1800, 90, List.of("WHITE"),
                    List.of(new ProfileSeries("UPVC-C60", "uPVC Casement 60", "CASEMENT", 60, 58, 76, 82, 450, 520, 480)))),
            List.of(new ColorDef("WHITE", "White", "#fff", 0)),
            List.of(new GlassDef("CLR5", "5mm Clear", 450)),
            List.of(new HardwareDef("CASEMENT_L", "Casement set", 1450), new HardwareDef("FIXED", "Beads", 80)));

    private final Design window = new Design("W1", "WINDOW", "UPVC", "UPVC-C60", "WHITE", "CLR5", 1800, 1500,
            List.of(600, 600, 600), List.of(1500),
            List.of(new Design.Panel(OpeningType.CASEMENT_L, Design.Fill.GLASS), new Design.Panel(OpeningType.FIXED, Design.Fill.GLASS),
                    new Design.Panel(OpeningType.CASEMENT_L, Design.Fill.GLASS)), true, false, 2);

    private static QuoteLine item(QuoteLine.Unit unit, Double qty, double rate) {
        return new QuoteLine(QuoteLine.Kind.ITEM, "Item", null, null, null, unit, qty, rate, null, null, null, null, null, null, null, null, null);
    }

    private QuoteLine design(QuoteLine.Pricing pricing, Double perSqft, List<BomLine> costLines, Double margin) {
        return new QuoteLine(QuoteLine.Kind.DESIGN, null, null, null, null, null, null, null, window, pricing, perSqft, costLines, margin,
                "W1", null, null, null);
    }

    @Test
    void itemsCountInTheirUnitsAndALumpSumIsItsRate() {
        assertEquals(2950.0, QuoteMath.line(catalog, item(QuoteLine.Unit.SQFT, 29.5, 100), true).total());
        assertEquals(3.0, QuoteMath.line(catalog, item(QuoteLine.Unit.NOS, 3.0, 1.0), true).qty());
        var lump = QuoteMath.line(catalog, item(QuoteLine.Unit.LUMPSUM, 4.0, 12500), true);
        assertEquals(QuoteLine.Unit.LUMPSUM, lump.unit());
        assertEquals(12500.0, lump.total());
        assertNull(QuoteMath.line(catalog, new QuoteLine(QuoteLine.Kind.SECTION, "Ground floor", null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null), true));
    }

    @Test
    void designsArePricedByCostingOrBySquareFoot() {
        var costing = QuoteMath.line(catalog, design(QuoteLine.Pricing.COSTING, null, null, null), true);
        assertEquals(20888.0, costing.unitPrice());
        assertEquals(41776.0, costing.total());
        var bySqft = QuoteMath.line(catalog, design(QuoteLine.Pricing.SQFT, 500.0, null, null), true);
        // 1800 x 1500 mm = 29.0626 sq ft; x 500 = 14531.28 a window; two windows rounded
        assertEquals(14531.28, bySqft.unitPrice());
        assertEquals(29063.0, bySqft.total());
        assertEquals(QuoteLine.Unit.SQFT, bySqft.unit());
    }

    @Test
    void anEditedCostingIsQuantityTimesRateThenTheMargin() {
        var base = new PricingEngine(catalog).calculate(window);
        List<BomLine> edited = new ArrayList<>(base.lines().subList(0, 2));
        edited.add(new BomLine("OTHER", "Transport to site", 1, "lot", 1000, 0));
        var r = QuoteMath.line(catalog, design(QuoteLine.Pricing.COSTING, null, edited, 10.0), true);
        double cost = Math.round((edited.get(0).amount() + edited.get(1).amount() + 1000) * 100) / 100.0;
        assertEquals(cost, r.price().costPerUnit());
        assertEquals(Math.round(cost * 1.1), r.unitPrice());
        assertTrue(r.price().warnings().contains("Costing edited by hand"));
    }

    @Test
    void totalsTakeTransportDiscountAndGst() {
        var lines = List.of(new QuoteLine.Priced(item(QuoteLine.Unit.NOS, 1.0, 10000), QuoteMath.line(catalog, item(QuoteLine.Unit.NOS, 1.0, 10000), true)));
        var t = QuoteMath.totals(lines, 500, 10, QuoteMath.DiscountType.PCT, QuoteMath.GstMode.ADD, 18);
        assertEquals(1000.0, t.discount());
        assertEquals(9500.0, t.taxable());
        assertEquals(1710.0, t.gst());
        assertEquals(11210.0, t.grandTotal());
        // a rupee discount is capped at half the subtotal; rates that include taxes add no GST
        var capped = QuoteMath.totals(lines, 0, 8000, QuoteMath.DiscountType.AMT, QuoteMath.GstMode.INCL, 18);
        assertEquals(5000.0, capped.discount());
        assertEquals(0.0, capped.gst());
        assertEquals(5000.0, capped.grandTotal());
    }
}
