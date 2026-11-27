/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.controller;

import com.companyportal.backend.dto.WindowQuoteDtos.*;
import com.companyportal.backend.security.AuthUser;
import com.companyportal.backend.security.Checkpoint;
import com.companyportal.backend.security.Menus;
import com.companyportal.backend.service.WindowQuoteService;
import com.companyportal.backend.windowquote.Design;
import com.companyportal.backend.windowquote.PriceBreakdown;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Product portal > Sales > Door & window quotes: the configurator's catalogue, pricing and the saved quotations. */
@RestController
@RequestMapping("/api/window-quotes")
@RequiredArgsConstructor
@Checkpoint({Menus.PROD_WINDOW_QUOTES})
public class WindowQuoteController {

    private final WindowQuoteService service;

    /** The catalogue (materials, series, colours, glass, hardware, settings) and the brands I may quote for. */
    @GetMapping("/setup")
    public Setup setup(@AuthenticationPrincipal AuthUser me) {
        return service.setup(me);
    }

    @PostMapping("/price")
    public PriceBreakdown price(@RequestBody Design design) {
        return service.price(design);
    }

    /** The texts and bank details of the latest quotation for the brand, to start a new one with (empty = none yet). */
    @GetMapping("/defaults")
    public QuoteOptions defaults(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) Long brandId) {
        return service.defaults(me, brandId);
    }

    @GetMapping
    public List<QuoteSummary> list(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) Long brandId) {
        return service.list(me, brandId);
    }

    @GetMapping("/{id}")
    public QuoteView get(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return service.get(me, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public QuoteView create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody QuoteBody body) {
        return service.create(me, body);
    }

    @PatchMapping("/{id}/status")
    public QuoteView status(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @Valid @RequestBody StatusBody body) {
        return service.status(me, id, body.status());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        service.delete(me, id);
    }
}
