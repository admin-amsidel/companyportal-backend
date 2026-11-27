/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.dto;

import com.companyportal.backend.entity.WindowQuote;
import com.companyportal.backend.windowquote.Catalog;
import com.companyportal.backend.windowquote.Design;
import com.companyportal.backend.windowquote.PriceBreakdown;
import com.companyportal.backend.windowquote.QuoteLine;
import com.companyportal.backend.windowquote.QuoteMath;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Door and window quotations: the configurator's catalogue, a new quotation, and what the screens show. */
public final class WindowQuoteDtos {

    private WindowQuoteDtos() {
    }

    /** The letterhead a quotation prints with: the brand's own details, logo and bank account. */
    public record Letterhead(Long id, String code, String name, String address, String phone, String email,
                             String gstNumber, String currency, Instant logoUpdatedAt, String bankAccountNumber, String ifscCode) {
    }

    /** What the configurator needs: the catalogue and the brands I may quote for. */
    public record Setup(Catalog catalog, List<Letterhead> brands) {
    }

    /** The printed texts and settings of a quotation (the Table and Design layout formats use different terms). */
    public record QuoteOptions(
            boolean roundLines,
            boolean gstSplit,
            boolean coverLetter,
            @Size(max = 4000) String letter,
            @Size(max = 6000) String termsTable,
            @Size(max = 6000) String termsDesign,
            @Size(max = 6000) String prerequisites,
            @Size(max = 150) String project,
            @Size(max = 100) String place,
            @Size(max = 150) String website,
            @Size(max = 150) String bankAccountName,
            @Size(max = 40) String bankAccountNo,
            @Size(max = 100) String bankName,
            @Size(max = 100) String bankBranch,
            @Size(max = 20) String bankIfsc) {
    }

    public record QuoteBody(
            /** The brand the quotation is for; owners and people without a brand choose, anyone else gets their own. */
            Long brandId,
            @NotBlank @Size(max = 150) String customerName,
            @Size(max = 30) String customerPhone,
            @Email @Size(max = 150) String customerEmail,
            @Size(max = 500) String siteAddress,
            @Size(max = 2000) String notes,
            @NotNull WindowQuote.Format format,
            @NotEmpty @Size(max = 200) @Valid List<QuoteLine> lines,
            @DecimalMin("0") BigDecimal transport,
            @DecimalMin("0") BigDecimal discountValue,
            QuoteMath.DiscountType discountType,
            QuoteMath.GstMode gstMode,
            @DecimalMin("0") @DecimalMax("40") BigDecimal gstPct,
            @Valid QuoteOptions options) {
    }

    public record StatusBody(@NotNull WindowQuote.Status status) {
    }

    /** Quotations saved before lines existed: one design per item. */
    public record LegacyItem(int position, Design design, PriceBreakdown price) {
    }

    public record QuoteView(Long id, String number, Letterhead brand, String createdBy, LocalDate quoteDate,
                            LocalDate validUntil, WindowQuote.Status status, String customerName, String customerPhone,
                            String customerEmail, String siteAddress, String notes, WindowQuote.Format format,
                            List<QuoteLine.Priced> lines, QuoteOptions options, BigDecimal subtotal, BigDecimal transport,
                            BigDecimal discountValue, QuoteMath.DiscountType discountType, BigDecimal discount,
                            QuoteMath.GstMode gstMode, BigDecimal gstPct, BigDecimal gst, BigDecimal grandTotal, String currency) {
    }

    public record QuoteSummary(Long id, String number, String brandCode, String createdBy, LocalDate quoteDate,
                               LocalDate validUntil, WindowQuote.Status status, WindowQuote.Format format, String customerName,
                               String customerPhone, int itemCount, BigDecimal grandTotal, String currency) {
    }
}
