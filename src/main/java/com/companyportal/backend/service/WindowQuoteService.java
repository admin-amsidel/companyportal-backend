/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.service;

import com.companyportal.backend.dto.WindowQuoteDtos.*;
import com.companyportal.backend.entity.Brand;
import com.companyportal.backend.entity.WindowQuote;
import com.companyportal.backend.exception.ApiException;
import com.companyportal.backend.exception.ResourceNotFoundException;
import com.companyportal.backend.repository.WindowQuoteRepository;
import com.companyportal.backend.security.AuthUser;
import com.companyportal.backend.windowquote.Catalog;
import com.companyportal.backend.windowquote.Design;
import com.companyportal.backend.windowquote.PriceBreakdown;
import com.companyportal.backend.windowquote.QuoteLine;
import com.companyportal.backend.windowquote.QuoteMath;
import com.companyportal.backend.windowquote.WindowCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Door and window quotations: every line is worked out again here when a quotation is saved (the browser's figures are
 * only a preview): design costings, sq ft rates, item quantities, then transport, the discount (at most half the
 * subtotal) and GST. The lines and texts are kept as a snapshot. Brands follow {@link TradeAccess}: owners and people
 * without a brand see every brand, anyone else only their own.
 */
@Service
@RequiredArgsConstructor
public class WindowQuoteService {

    private static final JsonMapper JSON = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    private final WindowQuoteRepository repo;
    private final WindowCatalogService catalog;
    private final TradeAccess access;
    private final TradeNumbers numbers;

    public Setup setup(AuthUser me) {
        return new Setup(catalog.get(), access.brands(me).stream().map(WindowQuoteService::letterhead).toList());
    }

    public PriceBreakdown price(Design design) {
        return catalog.price(design);
    }

    /** The texts and bank details of my latest quotation for the brand, to start the next one with; null = none yet. */
    @Transactional(readOnly = true)
    public QuoteOptions defaults(AuthUser me, Long brandId) {
        Long scope = access.scope(me, brandId);
        List<WindowQuote> all = scope == null ? repo.findAllByOrderByIdDesc() : repo.findByBrandIdOrderByIdDesc(scope);
        return all.stream().filter(q -> q.getOptionsJson() != null).findFirst()
                .map(q -> JSON.readValue(q.getOptionsJson(), QuoteOptions.class)).orElse(null);
    }

    @Transactional
    public QuoteView create(AuthUser me, QuoteBody body) {
        Brand brand = access.brandFor(me, body.brandId());
        Catalog cat = catalog.get();
        boolean roundLines = body.options() == null || body.options().roundLines();

        List<QuoteLine.Priced> lines = new ArrayList<>();
        int priced = 0;
        for (int i = 0; i < body.lines().size(); i++) {
            QuoteLine l = body.lines().get(i);
            try {
                QuoteLine.Result r = QuoteMath.line(cat, l, roundLines);
                if (r != null) priced++;
                lines.add(new QuoteLine.Priced(l, r));
            } catch (IllegalArgumentException | NullPointerException | IndexOutOfBoundsException e) {
                throw ApiException.badRequest("Line " + (i + 1) + " cannot be priced: " + e.getMessage());
            }
        }
        if (priced == 0) {
            throw ApiException.badRequest("Add at least one item or design, not only headings");
        }
        QuoteMath.DiscountType dType = body.discountType() == null ? QuoteMath.DiscountType.PCT : body.discountType();
        QuoteMath.GstMode gMode = body.gstMode() == null ? QuoteMath.GstMode.ADD : body.gstMode();
        double gstPct = body.gstPct() == null ? cat.settings().taxPct() : body.gstPct().doubleValue();
        QuoteMath.Totals t = QuoteMath.totals(lines, num(body.transport()), num(body.discountValue()), dType, gMode, gstPct);
        LocalDate today = access.today();

        WindowQuote q = new WindowQuote();
        q.setNumber(numbers.next(brand, TradeNumbers.WINDOW_QUOTE, today));
        q.setBrand(brand);
        q.setCreatedBy(access.me(me));
        q.setQuoteDate(today);
        q.setValidUntil(today.plusDays(cat.settings().quoteValidityDays()));
        q.setStatus(WindowQuote.Status.DRAFT);
        q.setFormat(body.format());
        q.setCustomerName(body.customerName().trim());
        q.setCustomerPhone(blank(body.customerPhone()));
        q.setCustomerEmail(blank(body.customerEmail()));
        q.setSiteAddress(blank(body.siteAddress()));
        q.setNotes(blank(body.notes()));
        q.setSubtotal(money(t.subtotal()));
        q.setTransport(money(t.transport()));
        q.setDiscountValue(money(num(body.discountValue())));
        q.setDiscountType(dType);
        q.setDiscount(money(t.discount()));
        q.setGstMode(gMode);
        q.setGstPct(money(gMode == QuoteMath.GstMode.ADD ? gstPct : 0));
        q.setGst(money(t.gst()));
        q.setGrandTotal(money(t.grandTotal()));
        q.setCurrency(brand.getCurrency().name());
        q.setItemCount(priced);
        q.setLinesJson(JSON.writeValueAsString(lines));
        q.setOptionsJson(body.options() == null ? null : JSON.writeValueAsString(body.options()));
        return view(repo.save(q));
    }

    @Transactional(readOnly = true)
    public List<QuoteSummary> list(AuthUser me, Long brandId) {
        Long scope = access.scope(me, brandId);
        List<WindowQuote> all = scope == null ? repo.findAllByOrderByIdDesc() : repo.findByBrandIdOrderByIdDesc(scope);
        return all.stream().map(q -> new QuoteSummary(q.getId(), q.getNumber(), q.getBrand().getCode(), name(q),
                q.getQuoteDate(), q.getValidUntil(), q.getStatus(), q.getFormat(), q.getCustomerName(), q.getCustomerPhone(),
                q.getItemCount(), q.getGrandTotal(), q.getCurrency())).toList();
    }

    @Transactional(readOnly = true)
    public QuoteView get(AuthUser me, Long id) {
        return view(find(me, id));
    }

    @Transactional
    public QuoteView status(AuthUser me, Long id, WindowQuote.Status status) {
        WindowQuote q = find(me, id);
        q.setStatus(status);
        return view(q);
    }

    @Transactional
    public void delete(AuthUser me, Long id) {
        access.requireAdmin(me, "Only owners and admins can delete quotations");
        repo.delete(find(me, id));
    }

    private WindowQuote find(AuthUser me, Long id) {
        WindowQuote q = repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Quotation not found"));
        access.check(me, q.getBrand());
        return q;
    }

    private QuoteView view(WindowQuote q) {
        List<QuoteLine.Priced> lines = q.getLinesJson() != null
                ? JSON.readValue(q.getLinesJson(), new TypeReference<List<QuoteLine.Priced>>() { })
                : legacy(q.getItemsJson());
        QuoteOptions options = q.getOptionsJson() == null ? null : JSON.readValue(q.getOptionsJson(), QuoteOptions.class);
        return new QuoteView(q.getId(), q.getNumber(), letterhead(q.getBrand()), name(q), q.getQuoteDate(), q.getValidUntil(),
                q.getStatus(), q.getCustomerName(), q.getCustomerPhone(), q.getCustomerEmail(), q.getSiteAddress(), q.getNotes(),
                q.getFormat(), lines, options, q.getSubtotal(), q.getTransport(), q.getDiscountValue(), q.getDiscountType(),
                q.getDiscount(), q.getGstMode(), q.getGstPct(), q.getGst(), q.getGrandTotal(), q.getCurrency());
    }

    /** A quotation saved before lines existed: each item becomes a design line priced by its costing. */
    private static List<QuoteLine.Priced> legacy(String itemsJson) {
        if (itemsJson == null) return List.of();
        List<LegacyItem> items = JSON.readValue(itemsJson, new TypeReference<List<LegacyItem>>() { });
        return items.stream().map(it -> {
            PriceBreakdown p = it.price();
            QuoteLine l = new QuoteLine(QuoteLine.Kind.DESIGN, null, null, null, null, null, null, null, it.design(),
                    QuoteLine.Pricing.COSTING, null, null, null, null, null, null, null);
            return new QuoteLine.Priced(l, new QuoteLine.Result(QuoteLine.Unit.NOS, (double) p.quantity(), p.unitPrice(), p.unitPrice(),
                    p.lineTotal(), Math.round(QuoteMath.sqft(it.design()) * 1000) / 1000.0, p));
        }).toList();
    }

    static Letterhead letterhead(Brand b) {
        return new Letterhead(b.getId(), b.getCode(), b.getName(), b.getAddress(), b.getContactNumber(), b.getEmail(),
                b.getGstNumber(), b.getCurrency().name(), b.getLogoUpdatedAt(), b.getBankAccountNumber(), b.getIfscCode());
    }

    private static String name(WindowQuote q) {
        return q.getCreatedBy() == null ? null : q.getCreatedBy().getName();
    }

    private static double num(BigDecimal v) {
        return v == null ? 0 : v.doubleValue();
    }

    private static BigDecimal money(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
