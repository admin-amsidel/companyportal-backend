/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.windowquote;

import com.companyportal.backend.windowquote.Catalog.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PricingEngineTest {

    private final Catalog catalog = new Catalog("INR",
            new Settings(8, 25, 18, 350, 250, 15),
            List.of(new MaterialDef("UPVC", "uPVC", 600, 1800, 90, List.of("WHITE"),
                    List.of(new ProfileSeries("UPVC-C60", "uPVC Casement 60", "CASEMENT", 60, 58, 76, 82, 450, 520, 480)))),
            List.of(new ColorDef("WHITE", "White", "#fff", 0)),
            List.of(new GlassDef("CLR5", "5mm Clear", 450)),
            List.of(new HardwareDef("CASEMENT_L", "Casement set", 1450), new HardwareDef("FIXED", "Beads", 80)));

    @Test
    void threePanelCasementWindow() {
        var fixed = new Design.Panel(OpeningType.FIXED, Design.Fill.GLASS);
        var casement = new Design.Panel(OpeningType.CASEMENT_L, Design.Fill.GLASS);
        var d = new Design("W1", "WINDOW", "UPVC", "UPVC-C60", "WHITE", "CLR5", 1800, 1500,
                List.of(600, 600, 600), List.of(1500), List.of(casement, fixed, casement), true, false, 2);

        var p = new PricingEngine(catalog).calculate(d);

        assertEquals(20888, p.unitPrice());
        assertEquals(41776, p.lineTotal());
        assertEquals(2.7, p.areaSqm());
        assertTrue(p.warnings().isEmpty());
    }

    @Test
    void divisionsAreNormalisedToOverallSize() {
        var d = new Design("W2", "WINDOW", "UPVC", "UPVC-C60", "WHITE", "CLR5", 1200, 1200,
                List.of(500, 500), List.of(1200), List.of(), false, false, 1);
        var p = new PricingEngine(catalog).calculate(d);
        assertFalse(p.warnings().isEmpty());
    }

    @Test
    void slidingChannelsReplaceMullionsAndRaiseTheFrameRate() {
        var slideCat = new Catalog("INR", catalog.settings(),
                List.of(new MaterialDef("UPVC", "uPVC", 600, 1800, 90, List.of("WHITE"),
                        List.of(new ProfileSeries("UPVC-S88", "uPVC Slider 88", "SLIDING", 88, 50, 60, 70, 400, 450, 430)))),
                catalog.colors(), catalog.glass(), catalog.hardware());
        var left = new Design.Panel(OpeningType.SLIDING_R, Design.Fill.GLASS, 3);
        var centre = new Design.Panel(OpeningType.FIXED, Design.Fill.GLASS, 1);
        var right = new Design.Panel(OpeningType.SLIDING_L, Design.Fill.GLASS, 3);
        var cols = List.of(600, 600, 600);

        var auto = new PricingEngine(slideCat).calculate(new Design("W3", "WINDOW", "UPVC", "UPVC-S88", "WHITE", "CLR5", 1800, 1200,
                cols, List.of(1200), List.of(left, centre, right), false, false, 1));
        var threeTrack = new PricingEngine(slideCat).calculate(new Design("W3", "WINDOW", "UPVC", "UPVC-S88", "WHITE", "CLR5", 1800, 1200,
                cols, List.of(1200), List.of(left, centre, right), false, false, 1, 3));

        // automatic: the fixed centre sits between two mullions; with channels it is a fixed sash at interlocks
        assertTrue(auto.lines().stream().anyMatch(l -> l.description().startsWith("Mullion")));
        assertTrue(threeTrack.lines().stream().noneMatch(l -> l.description().startsWith("Mullion")));
        var frame = threeTrack.lines().stream().filter(l -> l.description().contains("outer frame")).findFirst().orElseThrow();
        assertTrue(frame.description().contains("3-track"));
        assertEquals(600.0, frame.rate());   // 400 per m for two channels x 3/2
    }
}
