/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.windowquote;

import com.companyportal.backend.windowquote.Catalog.*;
import com.companyportal.backend.windowquote.Design.Fill;
import com.companyportal.backend.windowquote.Design.Panel;

import java.util.*;

/**
 * Pure costing engine: Design + Catalog -> BOM + price. No Spring dependency.
 * The Angular app has a mirror (pricing-engine.ts) for instant preview;
 * the server result is always the one saved on the quotation.
 */
public class PricingEngine {

    /** Sash overlaps frame/mullion rebate by 8mm each side. */
    private static final int SASH_OVERLAP = 8;

    private final Catalog catalog;

    public PricingEngine(Catalog catalog) {
        this.catalog = catalog;
    }

    public PriceBreakdown calculate(Design d) {
        List<String> warnings = new ArrayList<>();
        MaterialDef mat = catalog.material(d.material())
                .orElseThrow(() -> new IllegalArgumentException("Unknown material " + d.material()));
        ProfileSeries ps = catalog.series(mat, d.series())
                .orElseGet(() -> { warnings.add("Series not found, using " + mat.series().get(0).code()); return mat.series().get(0); });
        ColorDef color = catalog.color(d.color()).orElse(new ColorDef("STD", "Standard", "#ffffff", 0));
        GlassDef glass = catalog.glass(d.glass()).orElse(new GlassDef("NONE", "No glass", 0));
        Settings st = catalog.settings();

        int W = d.width(), H = d.height();
        List<Integer> cols = normalise(d.columns(), W, "column widths", warnings);
        List<Integer> rows = normalise(d.rows(), H, "row heights", warnings);
        int nc = cols.size(), nr = rows.size();
        double F = ps.frameFace(), M = ps.mullionFace(), S = ps.sashFace();
        double cf = 1 + color.surchargePct() / 100.0;

        // FRAME = frame only (e.g. WPC / wood door frame "chowkhat"): 3-sided, no shutters, no glass
        boolean frameOnly = "FRAME".equals(d.productType());
        if (frameOnly) { nc = 0; nr = 1; }

        // ---- profile lengths (mm)
        double frameMm = frameOnly ? 2.0 * H + W : 2.0 * (W + H);
        double mullionMm = 0;
        for (int r = 0; r < nr; r++) {
            for (int c = 1; c < nc; c++) {
                boolean bothSliding = d.onTrack(r, c - 1) && d.onTrack(r, c);
                if (!bothSliding) mullionMm += rows.get(r);   // sliding pairs meet at an interlock, not a mullion
            }
        }
        mullionMm += (double) (nr - 1) * W;                 // transoms run full width

        double sashMm = 0, glassSqm = 0, solidSqm = 0, openingSqm = 0;
        Map<String, double[]> hw = new LinkedHashMap<>();    // name -> [count, price]

        for (int r = 0; r < nr; r++) {
            for (int c = 0; c < nc; c++) {
                Panel p = d.panel(r, c);
                double cw = cols.get(c), ch = rows.get(r);
                double dl = c == 0 ? F : M / 2, dr = c == nc - 1 ? F : M / 2;
                double dt = r == 0 ? F : M / 2, db = r == nr - 1 ? F : M / 2;
                double clearW = cw - dl - dr, clearH = ch - dt - db;
                double gw = clearW, gh = clearH;
                if (p.opening().isOpening() || d.onTrack(r, c)) {
                    double sw = clearW + 2 * SASH_OVERLAP, sh = clearH + 2 * SASH_OVERLAP;
                    sashMm += 2 * (sw + sh);
                    gw = sw - 2 * S;
                    gh = sh - 2 * S;
                    if (p.opening().isOpening()) openingSqm += cw * ch / 1e6;
                }
                double a = Math.max(0, gw) * Math.max(0, gh) / 1e6;
                if (p.fill() == Fill.SOLID) solidSqm += a; else glassSqm += a;

                catalog.hardware(p.opening()).ifPresent(h ->
                        hw.computeIfAbsent(h.name(), k -> new double[]{0, h.price()})[0]++);
            }
        }

        // ---- BOM lines
        List<PriceBreakdown.BomLine> lines = new ArrayList<>();
        double profileCost = 0;
        // a frame with more than two sliding channels: the frame rate grows with the channels (rate is for 2)
        int tracks = 2;
        if (!frameOnly && d.tracks() != null && d.tracks() > 2) {
            for (int r = 0; r < rows.size(); r++) {
                if (d.rowSlides(r)) { tracks = d.tracks(); break; }
            }
        }
        profileCost += add(lines, "PROFILE", ps.name() + " - outer frame" + (tracks > 2 ? " (" + tracks + "-track)" : ""),
                frameMm / 1000, "m", ps.frameRatePerM() * cf * tracks / 2.0);
        if (mullionMm > 0)
            profileCost += add(lines, "PROFILE", "Mullion / transom", mullionMm / 1000, "m", ps.mullionRatePerM() * cf);
        if (sashMm > 0 && ps.sashRatePerM() > 0)
            profileCost += add(lines, "PROFILE", "Sash / shutter profile", sashMm / 1000, "m", ps.sashRatePerM() * cf);
        if (mat.reinforcementRatePerM() > 0)
            profileCost += add(lines, "PROFILE", "Steel reinforcement", (frameMm + mullionMm + sashMm) / 1000, "m", mat.reinforcementRatePerM());

        if (glassSqm > 0) {
            if (glass.ratePerSqm() <= 0) warnings.add("Glass panels present but no glass type selected");
            else add(lines, "GLASS", glass.name(), glassSqm, "sqm", glass.ratePerSqm());
        }
        if (solidSqm > 0)
            add(lines, "PANEL", mat.name() + " solid panel / board", solidSqm, "sqm", mat.solidPanelRatePerSqm() * cf);

        hw.forEach((name, v) -> add(lines, "HARDWARE", name, v[0], "set", v[1]));

        if (d.mesh() && openingSqm > 0)
            add(lines, "ACCESSORY", "Mosquito mesh", openingSqm, "sqm", st.meshRatePerSqm());

        double materialCost = lines.stream().mapToDouble(PriceBreakdown.BomLine::amount).sum();
        double wastage = round2(profileCost * st.wastagePct() / 100);
        lines.add(new PriceBreakdown.BomLine("WASTAGE", "Profile cutting wastage " + st.wastagePct() + "%", 1, "lot", wastage, wastage));

        double areaSqm = W * (double) H / 1e6;
        double labour = round2(areaSqm * mat.labourRatePerSqm());
        lines.add(new PriceBreakdown.BomLine("LABOUR", "Fabrication labour", round3(areaSqm), "sqm", mat.labourRatePerSqm(), labour));
        double installation = 0;
        if (d.installation()) {
            installation = round2(areaSqm * st.installationRatePerSqm());
            lines.add(new PriceBreakdown.BomLine("LABOUR", "Installation at site", round3(areaSqm), "sqm", st.installationRatePerSqm(), installation));
        }

        double cost = round2(materialCost + wastage + labour + installation);
        double margin = round2(cost * st.marginPct() / 100);
        double unitPrice = Math.round(cost + margin);
        int qty = Math.max(1, d.quantity());

        return new PriceBreakdown(lines, round3(areaSqm), round2(materialCost), wastage, labour, installation,
                cost, margin, unitPrice, qty, unitPrice * qty, warnings);
    }

    private static double add(List<PriceBreakdown.BomLine> lines, String cat, String desc, double qty, String unit, double rate) {
        double q = round3(qty), r = round2(rate), amt = round2(q * r);
        lines.add(new PriceBreakdown.BomLine(cat, desc, q, unit, r, amt));
        return amt;
    }

    /** Ensures divisions are present and add up to the overall size (last division absorbs the difference). */
    static List<Integer> normalise(List<Integer> parts, int total, String what, List<String> warnings) {
        if (parts == null || parts.isEmpty()) return List.of(total);
        List<Integer> out = new ArrayList<>(parts);
        int sum = out.stream().mapToInt(Integer::intValue).sum();
        if (sum != total) {
            int last = out.get(out.size() - 1) + (total - sum);
            if (last <= 0) {
                warnings.add("Invalid " + what + ", divided equally");
                int n = out.size(), each = total / n;
                out.replaceAll(x -> each);
                out.set(n - 1, total - each * (n - 1));
            } else {
                warnings.add("Adjusted last of " + what + " by " + (total - sum) + "mm to match overall size");
                out.set(out.size() - 1, last);
            }
        }
        return out;
    }

    static double round2(double v) { return Math.round(v * 100) / 100.0; }
    static double round3(double v) { return Math.round(v * 1000) / 1000.0; }
}
