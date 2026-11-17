/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.service;

import com.companyportal.backend.entity.ProfessionalTaxSlab;
import com.companyportal.backend.entity.SalaryStructure;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The pay rules the business chose (Payroll questions, 17 November 2026), month by month. */
class PayEngineTest {

    private static int employed(String from, String to, int workingDays) {
        LocalDate f = LocalDate.parse(from);
        return PayEngine.employedDays(new PayEngine.Span(f, LocalDate.parse(to)), YearMonth.from(f), workingDays);
    }

    @Test
    void aPartMonthPaysEveryDayFromTheStartSundaysIncluded() {
        // Q1: a salary from 9 November 2026 is paid 22 of 30 days, like a full month counts its Sundays
        assertThat(employed("2026-11-09", "2026-11-30", 30)).isEqualTo(22);
        assertThat(PayEngine.forDays(new BigDecimal("20000"), new BigDecimal("22"), 30)).isEqualByComparingTo("14667");
        // a whole month is all its working days
        assertThat(employed("2026-11-01", "2026-11-30", 30)).isEqualTo(30);
        assertThat(PayEngine.forDays(new BigDecimal("20000"), new BigDecimal("30"), 30)).isEqualByComparingTo("20000");
    }

    @Test
    void theCalendarDaysAreScaledToTheMonthsWorkingDays() {
        // a 31-day month: from the 2nd = 30 of 31 days = 29 of 30
        assertThat(employed("2026-01-02", "2026-01-31", 30)).isEqualTo(29);
        // leaving on 15 January: 15 of 31 = 14.5, rounded to 15
        assertThat(employed("2026-01-01", "2026-01-15", 30)).isEqualTo(15);
        // February (28 days) from the 15th: 14 of 28 = 15 of 30
        assertThat(employed("2026-02-15", "2026-02-28", 30)).isEqualTo(15);
        // a month of 26 working days scales the same way
        assertThat(employed("2026-11-09", "2026-11-30", 26)).isEqualTo(19);
    }

    @Test
    void halfDaysOfLossOfPay() {
        assertThat(PayEngine.forDays(new BigDecimal("30000"), new BigDecimal("28.5"), 30)).isEqualByComparingTo("28500");
    }

    @Test
    void epfOnTheWageCeilingUnlessOnTheFullWage() {
        SalaryStructure capped = new SalaryStructure();
        assertThat(PayEngine.pf(new BigDecimal("25000"), capped)).isEqualByComparingTo("1800");
        assertThat(PayEngine.pf(new BigDecimal("10667"), capped)).isEqualByComparingTo("1280");
        SalaryStructure full = new SalaryStructure();
        full.setPfFullWage(true);
        assertThat(PayEngine.pf(new BigDecimal("25000"), full)).isEqualByComparingTo("3000");
    }

    @Test
    void esiIsRoundedUpToTheRupee() {
        assertThat(PayEngine.esi(new BigDecimal("20000"))).isEqualByComparingTo("150");
        assertThat(PayEngine.esi(new BigDecimal("13333"))).isEqualByComparingTo("100");
    }

    @Test
    void professionalTaxIsTheHighestSlabReached() {
        List<ProfessionalTaxSlab> slabs = List.of(slab(0, 0), slab(12_000, 120), slab(60_000, 600), slab(125_000, 1250));
        assertThat(PayEngine.halfYearTax(slabs, new BigDecimal("11999"))).isEqualByComparingTo("0");
        assertThat(PayEngine.halfYearTax(slabs, new BigDecimal("12000"))).isEqualByComparingTo("120");
        assertThat(PayEngine.halfYearTax(slabs, new BigDecimal("120000"))).isEqualByComparingTo("600");
        assertThat(PayEngine.halfYearTax(slabs, new BigDecimal("500000"))).isEqualByComparingTo("1250");
        // April to September, October to March
        assertThat(PayEngine.half(YearMonth.of(2026, 9))).isZero();
        assertThat(PayEngine.half(YearMonth.of(2026, 10))).isEqualTo(1);
        assertThat(PayEngine.half(YearMonth.of(2027, 3))).isEqualTo(1);
    }

    private static ProfessionalTaxSlab slab(long from, long tax) {
        ProfessionalTaxSlab s = new ProfessionalTaxSlab();
        s.setFromIncome(BigDecimal.valueOf(from));
        s.setHalfYearTax(BigDecimal.valueOf(tax));
        return s;
    }
}
