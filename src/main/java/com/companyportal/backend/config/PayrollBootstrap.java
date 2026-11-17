/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.config;

import com.companyportal.backend.entity.PayComponent;
import com.companyportal.backend.entity.PayComponentKind;
import com.companyportal.backend.entity.ProfessionalTaxSlab;
import com.companyportal.backend.repository.PayComponentRepository;
import com.companyportal.backend.repository.ProfessionalTaxSlabRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

import static com.companyportal.backend.entity.PayComponentKind.*;

/**
 * First start: the pay components of the standard payslip, and professional tax slabs (Kerala's, per half year) when
 * there are none. ESI is added to components made before payroll worked it out. Owners and HR change them under
 * Payroll > Pay components.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayrollBootstrap implements ApplicationRunner {

    /** A half year's gross from (rupees) and its professional tax: Kerala's slabs. */
    private static final long[][] KERALA_PT = {{0, 0}, {12_000, 120}, {18_000, 180}, {30_000, 300}, {45_000, 450}, {60_000, 600},
            {75_000, 750}, {100_000, 1000}, {125_000, 1250}};

    private final PayComponentRepository components;
    private final ProfessionalTaxSlabRepository slabs;

    @Override
    public void run(ApplicationArguments args) {
        if (slabs.count() == 0) {
            for (long[] s : KERALA_PT) {
                ProfessionalTaxSlab slab = new ProfessionalTaxSlab();
                slab.setFromIncome(BigDecimal.valueOf(s[0]).setScale(2));
                slab.setHalfYearTax(BigDecimal.valueOf(s[1]).setScale(2));
                slabs.save(slab);
            }
            log.info("Added the professional tax slabs (Kerala, per half year)");
        }
        if (components.count() > 0) {
            markTaxes();
            spellOut();
            addEsi();
            return;
        }
        add("BASIC", "Basic", EARNING, 10);
        add("DA", "Dearness allowance", EARNING, 20);
        add("HRA", "House Rent Allowance", EARNING, 30);
        add("LTA", "Leave Travel Allowance", EARNING, 40);
        add("SPECIAL", "Special allowance", EARNING, 50);
        add("EPF", "EPF Contribution", DEDUCTION, 10);
        add("ESI", "ESI Contribution", DEDUCTION, 15);
        add("LWF", "Labour Welfare Fund", DEDUCTION, 20);
        add("TDS", "Income Tax", DEDUCTION, 30, true);
        add("PT", "Professional Tax", DEDUCTION, 40, true);
        add("FUEL", "Fuel Reimbursement", REIMBURSEMENT, 10);
        add("PHONE", "Telephone Reimbursement", REIMBURSEMENT, 20);
        log.info("Created the standard pay components");
    }

    private void add(String code, String name, PayComponentKind kind, int order) {
        add(code, name, kind, order, false);
    }

    private void add(String code, String name, PayComponentKind kind, int order, boolean tax) {
        PayComponent c = new PayComponent();
        c.setCode(code);
        c.setName(name);
        c.setKind(kind);
        c.setSortOrder(order);
        c.setTax(tax);
        components.save(c);
    }

    /** ESI, worked out by payroll since it was added: components made before it get it. */
    private void addEsi() {
        if (!components.existsByCodeIgnoreCase("ESI")) {
            add("ESI", "ESI Contribution", DEDUCTION, 15);
            log.info("Added the ESI Contribution pay component");
        }
    }

    /** Components made with a short name: LTA is Leave Travel Allowance (a name changed by hand is kept). */
    private void spellOut() {
        int n = components.rename("LTA", "LTA", "Leave Travel Allowance");
        int lines = components.renameOnPayslips("LTA", "LTA", "Leave Travel Allowance");
        if (n + lines > 0) {
            log.info("Renamed LTA to Leave Travel Allowance ({} component, {} payslip lines)", n, lines);
        }
    }

    /** Components made before the "tax" flag existed: Income Tax and Professional Tax are taxes. */
    private void markTaxes() {
        if (components.findAll().stream().anyMatch(PayComponent::isTax)) {
            return;
        }
        components.findAll().stream().filter(c -> c.getCode().equals("TDS") || c.getCode().equals("PT")).forEach(c -> {
            c.setTax(true);
            components.save(c);
        });
    }
}
