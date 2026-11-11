/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.service;

import com.companyportal.backend.entity.Brand;
import com.companyportal.backend.entity.TradeMailBrandConfig;
import com.companyportal.backend.entity.TradeMailDailyCount;
import com.companyportal.backend.repository.BrandRepository;
import com.companyportal.backend.repository.TradeMailBrandConfigRepository;
import com.companyportal.backend.repository.TradeMailDailyCountRepository;
import com.companyportal.backend.security.AuthUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** Customer &amp; supplier email limits per brand (Customer portal, Email > config); see MailLimitsService. */
@Service
public class TradeMailConfig extends MailLimitsService<TradeMailBrandConfig, TradeMailDailyCount> {

    private final TradeAccess access;

    public TradeMailConfig(TradeMailBrandConfigRepository configs, TradeMailDailyCountRepository counts, BrandRepository brands,
                           TradeAccess access, @Value("${app.leave.zone}") ZoneId zone,
                           @Value("${app.mail.always-cc:}") String defaultCc, BrandMailSenders senders) {
        super(configs, counts, brands, zone, defaultCc, senders);
        this.access = access;
    }

    @Override
    protected List<Brand> brandsFor(AuthUser me) {
        return access.brands(me);
    }

    /** Customers and suppliers always have a brand. */
    @Override
    protected boolean seesNoBrand(AuthUser me) {
        return false;
    }

    @Override
    protected int defaultPickLimit() {
        return DEFAULT_PICK_LIMIT;
    }

    @Override
    protected String defaultFooter(String brandName) {
        return "Sent by " + brandName;
    }

    @Override
    protected TradeMailBrandConfig newConfig(long brandKey) {
        TradeMailBrandConfig c = new TradeMailBrandConfig();
        c.setBrandKey(brandKey);
        return c;
    }

    @Override
    protected TradeMailDailyCount newCount(long brandKey, LocalDate day) {
        TradeMailDailyCount c = new TradeMailDailyCount();
        c.setBrandKey(brandKey);
        c.setSentOn(day);
        return c;
    }

    @Override
    protected String configPage() {
        return "Project portal > Email > Email config";
    }

    @Override
    protected String mailName() {
        return "customer & supplier email";
    }
}
