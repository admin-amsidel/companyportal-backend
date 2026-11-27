/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.windowquote;

import java.util.List;
import java.util.Optional;

/**
 * Master data: materials, profile series, colours, glass and hardware.
 * Loaded from catalog.json at startup (move to DB tables + admin screens in phase 2).
 */
public record Catalog(
        String currency,
        Settings settings,
        List<MaterialDef> materials,
        List<ColorDef> colors,
        List<GlassDef> glass,
        List<HardwareDef> hardware) {

    public record Settings(double wastagePct, double marginPct, double taxPct,
                           double meshRatePerSqm, double installationRatePerSqm,
                           int quoteValidityDays) {}

    public record MaterialDef(String code, String name, double labourRatePerSqm,
                              double solidPanelRatePerSqm, double reinforcementRatePerM,
                              List<String> colors, List<ProfileSeries> series) {}

    /** Face widths and depth in mm, rates per running metre. */
    public record ProfileSeries(String code, String name, String kind, int frameDepth,
                                int frameFace, int sashFace, int mullionFace,
                                double frameRatePerM, double sashRatePerM, double mullionRatePerM) {}

    public record ColorDef(String code, String name, String hex, double surchargePct) {}

    public record GlassDef(String code, String name, double ratePerSqm) {}

    public record HardwareDef(String opening, String name, double price) {}

    public Optional<MaterialDef> material(String code) {
        return materials.stream().filter(m -> m.code().equals(code)).findFirst();
    }

    public Optional<ProfileSeries> series(MaterialDef m, String code) {
        return m.series().stream().filter(s -> s.code().equals(code)).findFirst();
    }

    public Optional<ColorDef> color(String code) {
        return colors.stream().filter(c -> c.code().equals(code)).findFirst();
    }

    public Optional<GlassDef> glass(String code) {
        return glass.stream().filter(g -> g.code().equals(code)).findFirst();
    }

    public Optional<HardwareDef> hardware(OpeningType opening) {
        return hardware.stream().filter(h -> h.opening().equals(opening.name())).findFirst();
    }
}
