/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.windowquote;

import com.companyportal.backend.windowquote.PriceBreakdown.BomLine;

import java.util.ArrayList;
import java.util.List;

/**
 * Quotation sums, the same as quote-math.ts in the portal: each line's total, then subtotal, transport, discount, GST
 * and the grand total. Kept free of Spring so it can be tested on its own.
 */
public final class QuoteMath {

    /** Square millimetres in a square foot. */
    public static final double MM2_PER_SQFT = 92903.04;
    /** A discount never takes more than half the subtotal. */
    public static final double MAX_DISCOUNT_SHARE = 0.5;

    public enum DiscountType { PCT, AMT }

    public enum GstMode { ADD, INCL }

    public record Totals(double subtotal, double transport, double discount, double taxable, double gst, double grandTotal,
                         int pieces, double areaSqft) {
    }

    private QuoteMath() {
    }

    public static double sqft(Design d) {
        return d.width() * (double) d.height() / MM2_PER_SQFT;
    }

    /** The costing of a design: as calculated, or with the hand-edited lines and margin. */
    public static PriceBreakdown costing(Catalog catalog, QuoteLine l) {
        PriceBreakdown base = new PricingEngine(catalog).calculate(l.design());
        boolean edited = l.costLines() != null && !l.costLines().isEmpty();
        if (!edited && l.marginPct() == null) {
            return base;
        }
        double margin = l.marginPct() != null ? clamp(l.marginPct(), 0, 300) : catalog.settings().marginPct();
        return fromLines(base, edited ? l.costLines() : base.lines(), margin, edited);
    }

    /** Every line is its quantity times its rate; the cost is their sum, then the margin. */
    static PriceBreakdown fromLines(PriceBreakdown base, List<BomLine> in, double marginPct, boolean edited) {
        List<BomLine> lines = new ArrayList<>();
        double material = 0, wastage = 0, labour = 0;
        for (BomLine b : in) {
            double qty = round3(Math.max(0, b.qty())), rate = round2(Math.max(0, b.rate())), amount = round2(qty * rate);
            String cat = b.category() == null || b.category().isBlank() ? "OTHER" : b.category();
            lines.add(new BomLine(cat, b.description() == null ? "" : b.description(), qty, b.unit() == null ? "" : b.unit(), rate, amount));
            if ("WASTAGE".equals(cat)) wastage += amount;
            else if ("LABOUR".equals(cat)) labour += amount;
            else material += amount;
        }
        double cost = round2(material + wastage + labour);
        double margin = round2(cost * marginPct / 100);
        double unit = Math.round(cost + margin);
        List<String> warnings = new ArrayList<>(base.warnings());
        if (edited) warnings.add("Costing edited by hand");
        return new PriceBreakdown(lines, base.areaSqm(), round2(material), round2(wastage), round2(labour), 0, cost, margin,
                unit, base.quantity(), unit * base.quantity(), warnings);
    }

    /** One line's result; a SECTION has none. */
    public static QuoteLine.Result line(Catalog catalog, QuoteLine l, boolean roundLines) {
        return switch (l.kind()) {
            case SECTION -> null;
            case ITEM -> {
                double rate = Math.max(0, nz(l.rate()));
                QuoteLine.Unit unit = l.unit() == null ? QuoteLine.Unit.NOS : l.unit();
                boolean lump = unit == QuoteLine.Unit.LUMPSUM || l.qty() == null;
                double qty = lump ? 0 : Math.max(0, l.qty());
                double total = fin(lump ? rate : qty * rate, roundLines);
                yield new QuoteLine.Result(lump ? QuoteLine.Unit.LUMPSUM : unit, lump ? null : round3(qty), rate, rate, total, null, null);
            }
            case DESIGN -> {
                if (l.design() == null) throw new IllegalArgumentException("A design line needs its design");
                PriceBreakdown price = costing(catalog, l);
                double area = sqft(l.design());
                int qty = Math.max(1, l.design().quantity());
                if (l.pricing() == QuoteLine.Pricing.SQFT) {
                    double rate = Math.max(0, nz(l.ratePerSqft()));
                    double unitPrice = round2(area * rate);
                    yield new QuoteLine.Result(QuoteLine.Unit.SQFT, round3(area * qty), rate, unitPrice, fin(unitPrice * qty, roundLines), round3(area), price);
                }
                yield new QuoteLine.Result(QuoteLine.Unit.NOS, (double) qty, price.unitPrice(), price.unitPrice(),
                        fin(price.unitPrice() * qty, roundLines), round3(area), price);
            }
        };
    }

    public static Totals totals(List<QuoteLine.Priced> lines, double transport, double discountValue, DiscountType type,
                                GstMode mode, double gstPct) {
        double sub = 0, area = 0;
        int pieces = 0;
        for (QuoteLine.Priced p : lines) {
            if (p.result() == null) continue;
            sub += p.result().total();
            if (p.line().kind() == QuoteLine.Kind.DESIGN) {
                int q = Math.max(1, p.line().design().quantity());
                pieces += q;
                area += sqft(p.line().design()) * q;
            }
        }
        sub = round2(sub);
        double dv = Math.max(0, discountValue);
        double discount = round2(Math.min(sub * MAX_DISCOUNT_SHARE, type == DiscountType.AMT ? dv : sub * dv / 100));
        double tr = round2(Math.max(0, transport));
        double taxable = round2(sub - discount + tr);
        double gst = mode == GstMode.ADD ? round2(taxable * Math.max(0, gstPct) / 100) : 0;
        return new Totals(sub, tr, discount, taxable, gst, round2(taxable + gst), pieces, round2(area));
    }

    private static double fin(double v, boolean round) {
        return round ? Math.round(v) : round2(v);
    }

    private static double nz(Double v) {
        return v == null ? 0 : v;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    static double round3(double v) {
        return Math.round(v * 1000) / 1000.0;
    }
}
