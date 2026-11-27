/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A door and window quotation (Product portal > Sales > Door & window quotes): the customer, the totals, and the items
 * as a JSON snapshot (each design with its priced bill of materials at the time of quoting), so later price list
 * changes never alter an old quote.
 */
@Entity
@Table(name = "window_quotes", indexes = @Index(name = "ix_window_quote_brand", columnList = "brand_id"))
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class WindowQuote {

    public enum Status { DRAFT, SENT, ACCEPTED, REJECTED }

    /** TABLE: the particulars table; DESIGN: a page block per door or window with its drawings. */
    public enum Format { TABLE, DESIGN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String number;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "brand_id", nullable = false)
    private Brand brand;

    /** Who made it. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private Employee createdBy;

    @Column(nullable = false)
    private LocalDate quoteDate;

    @Column(nullable = false)
    private LocalDate validUntil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.DRAFT;

    @Column(nullable = false, length = 150)
    private String customerName;

    @Column(length = 30)
    private String customerPhone;

    @Column(length = 150)
    private String customerEmail;

    @Column(length = 500)
    private String siteAddress;

    @Column(length = 2000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private Format format = Format.DESIGN;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal transport = BigDecimal.ZERO;

    /** A percentage or an amount, as discountType says. */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal discountValue = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private com.companyportal.backend.windowquote.QuoteMath.DiscountType discountType = com.companyportal.backend.windowquote.QuoteMath.DiscountType.PCT;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal discount;

    /** ADD: GST on top of the total; INCL: the rates include all taxes. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private com.companyportal.backend.windowquote.QuoteMath.GstMode gstMode = com.companyportal.backend.windowquote.QuoteMath.GstMode.ADD;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal gstPct;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal gst;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal grandTotal;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private int itemCount;

    /** Quotations from before lines existed: their designs with the priced bill of materials, as JSON. */
    @Column(columnDefinition = "text")
    private String itemsJson;

    /** The lines in order (designs, items, headings) with what was worked out for each, as JSON. */
    @Column(columnDefinition = "text")
    private String linesJson;

    /** The printed texts and settings (terms, cover letter, bank details), as JSON. */
    @Column(columnDefinition = "text")
    private String optionsJson;

    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
