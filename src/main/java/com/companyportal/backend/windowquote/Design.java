/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.windowquote;


import java.util.List;

/**
 * One configured product (a single line item of a quotation).
 * The layout is a grid: column widths (left to right) and row heights (top to bottom), all in mm.
 * panels is row-major: index = row * columns.size() + col.
 */
public record Design(
        String label,          // e.g. "W1 - Bedroom"
        String productType,    // WINDOW | DOOR | FRAME
        String material,       // UPVC | ALUMINIUM | WPC | WOOD | STEEL
        String series,         // profile series code
        String color,
        String glass,
        int width,
        int height,
        List<Integer> columns,
        List<Integer> rows,
        List<Panel> panels,
        boolean mesh,
        boolean installation,
        int quantity,
        Integer tracks) {     // sliding channels (2-4); null = automatic (sashes alternate on two)

    public enum Fill { GLASS, SOLID }

    /** track: the sliding channel, 1 = front (outside) ... tracks = back (inside); used when tracks is set. */
    public record Panel(OpeningType opening, Fill fill, Integer track) {
        public Panel(OpeningType opening, Fill fill) { this(opening, fill, null); }
    }

    /** The same design without a channel layout (older quotes and tests). */
    public Design(String label, String productType, String material, String series, String color, String glass, int width,
                  int height, List<Integer> columns, List<Integer> rows, List<Panel> panels, boolean mesh, boolean installation,
                  int quantity) {
        this(label, productType, material, series, color, glass, width, height, columns, rows, panels, mesh, installation, quantity, null);
    }

    public Panel panel(int row, int col) {
        int idx = row * columns.size() + col;
        if (panels == null || idx >= panels.size() || panels.get(idx) == null) {
            return new Panel(OpeningType.FIXED, Fill.GLASS);
        }
        return panels.get(idx);
    }

    /** A row with at least one sliding sash. */
    public boolean rowSlides(int row) {
        for (int c = 0; c < columns.size(); c++) {
            if (panel(row, c).opening().isSliding()) return true;
        }
        return false;
    }

    /** The panel runs in a sliding channel: a sliding sash, or (with the channels set) a fixed sash in a sliding row. */
    public boolean onTrack(int row, int col) {
        Panel p = panel(row, col);
        if (p.opening().isSliding()) return true;
        return tracks != null && tracks > 1 && p.opening() == OpeningType.FIXED && !"FRAME".equals(productType) && rowSlides(row);
    }
}
