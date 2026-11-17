/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.service;

import com.companyportal.backend.entity.TaxRegime;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** The yearly tax worked out by hand from the slabs, and the monthly share. */
class IncomeTaxTest {

    private static IncomeTax.Calc newer(int fy, long gross) {
        return IncomeTax.calc(TaxRegime.NEW, fy, BigDecimal.valueOf(gross), BigDecimal.ZERO, null, 12);
    }

    @Test
    void newRegimeFy2025NoTaxUpTo12LakhAfterTheStandardDeduction() {
        IncomeTax.Calc c = newer(2025, 1_275_000);
        assertThat(c.standardDeduction()).isEqualByComparingTo("75000");
        assertThat(c.taxable()).isEqualByComparingTo("1200000");
        assertThat(c.slabTax()).isEqualByComparingTo("60000");
        assertThat(c.rebate()).isEqualByComparingTo("60000");
        assertThat(c.annualTax()).isEqualByComparingTo("0");
    }

    @Test
    void newRegimeMarginalReliefJustAbove12Lakh() {
        // 12.1 lakh taxable: slab tax 61,500, but at most the 10,000 above 12 lakh; + 4% cess
        IncomeTax.Calc c = newer(2025, 1_285_000);
        assertThat(c.taxable()).isEqualByComparingTo("1210000");
        assertThat(c.rebate()).isEqualByComparingTo("51500");
        assertThat(c.annualTax()).isEqualByComparingTo("10400");
    }

    @Test
    void newRegimeHigherSlabsAndCess() {
        // 13 lakh: 60,000 + 15% of 1 lakh = 75,000 + cess 3,000
        assertThat(newer(2025, 1_375_000).annualTax()).isEqualByComparingTo("78000");
        // 25 lakh: 3,00,000 to 24 lakh + 30% of 1 lakh = 3,30,000 + cess 13,200
        IncomeTax.Calc c = newer(2025, 2_575_000);
        assertThat(c.annualTax()).isEqualByComparingTo("343200");
        assertThat(c.monthlyTax()).isEqualByComparingTo("28600");
    }

    @Test
    void surchargeAbove50Lakh() {
        // 60 lakh taxable: 13,80,000 + 10% surcharge 1,38,000 + cess 60,720
        IncomeTax.Calc c = newer(2025, 6_075_000);
        assertThat(c.slabTax()).isEqualByComparingTo("1380000");
        assertThat(c.surcharge()).isEqualByComparingTo("138000");
        assertThat(c.annualTax()).isEqualByComparingTo("1578720");
    }

    @Test
    void lastYearsNewRegimeRules() {
        // FY 2024-25: 10 lakh taxable = 20,000 + 30,000 + cess 2,000
        assertThat(newer(2024, 1_075_000).annualTax()).isEqualByComparingTo("52000");
        assertThat(newer(2024, 775_000).annualTax()).isEqualByComparingTo("0");
    }

    @Test
    void oldRegimeTakesOffProfessionalTaxAndTheDeclaredDeductions() {
        // 10 lakh - 50,000 - 2,400 PT - 1,50,000 declared = 7,97,600: 12,500 + 20% of 2,97,600 = 72,020 + cess 2,880.80 = 74,900.80 -> 74,900
        IncomeTax.Calc c = IncomeTax.calc(TaxRegime.OLD, 2025, BigDecimal.valueOf(1_000_000), BigDecimal.valueOf(2_400),
                BigDecimal.valueOf(150_000), 12);
        assertThat(c.taxable()).isEqualByComparingTo("797600");
        assertThat(c.annualTax()).isEqualByComparingTo("74900");
        assertThat(c.monthlyTax()).isEqualByComparingTo("6242");
        // up to 5 lakh taxable: the rebate takes it all
        assertThat(IncomeTax.calc(TaxRegime.OLD, 2025, BigDecimal.valueOf(550_000), BigDecimal.ZERO, null, 12).annualTax())
                .isEqualByComparingTo("0");
    }

    @Test
    void joinedMidYearTheTaxIsSpreadOverTheMonthsPaid() {
        // 7 months of 2 lakh = 14 lakh: 13.25 lakh taxable = 60,000 + 15% of 1.25 lakh = 78,750 + cess 3,150 = 81,900; / 7
        IncomeTax.Calc c = IncomeTax.calc(TaxRegime.NEW, 2025, BigDecimal.valueOf(1_400_000), BigDecimal.ZERO, null, 7);
        assertThat(c.annualTax()).isEqualByComparingTo("81900");
        assertThat(c.monthlyTax()).isEqualByComparingTo("11700");
    }

    @Test
    void surchargeMarginalReliefJustAbove50Lakh() {
        // Q4. 50.1 lakh taxable: 10,83,000 tax; 10% surcharge would be 1,08,300, but tax + surcharge may be at most the tax
        // at 50 lakh (10,80,000) + the 10,000 above it: surcharge 7,000; cess 43,600
        IncomeTax.Calc c = newer(2025, 5_085_000);
        assertThat(c.taxable()).isEqualByComparingTo("5010000");
        assertThat(c.slabTax()).isEqualByComparingTo("1083000");
        assertThat(c.surcharge()).isEqualByComparingTo("7000");
        assertThat(c.annualTax()).isEqualByComparingTo("1133600");
    }

    @Test
    void surchargeAbove1CroreAndTheNewRegimesTopRate() {
        // 1.05 crore: 27,30,000 + 15% = 4,09,500 (below the relief cap) + cess 1,25,580
        IncomeTax.Calc crore = newer(2025, 10_575_000);
        assertThat(crore.slabTax()).isEqualByComparingTo("2730000");
        assertThat(crore.surcharge()).isEqualByComparingTo("409500");
        assertThat(crore.annualTax()).isEqualByComparingTo("3265080");
        // 6 crore: the new regime stops at 25%; 1,75,80,000 + 43,95,000 + cess 8,79,000
        IncomeTax.Calc six = newer(2025, 60_075_000);
        assertThat(six.surcharge()).isEqualByComparingTo("4395000");
        assertThat(six.annualTax()).isEqualByComparingTo("22854000");
        // the old regime goes to 37% above 5 crore: 1,78,12,500 + 65,90,625 + cess 9,76,125
        IncomeTax.Calc old = IncomeTax.calc(TaxRegime.OLD, 2025, BigDecimal.valueOf(60_050_000), BigDecimal.ZERO, null, 12);
        assertThat(old.slabTax()).isEqualByComparingTo("17812500");
        assertThat(old.surcharge()).isEqualByComparingTo("6590625");
        assertThat(old.annualTax()).isEqualByComparingTo("25379250");
    }

    @Test
    void seniorCitizensUnderTheOldRegime() {
        // 8 lakh taxable: 72,500 + cess = 75,400; at 60 nil to 3 lakh: 70,000 + cess = 72,800; at 80 nil to 5 lakh: 60,000 + cess = 62,400
        BigDecimal income = BigDecimal.valueOf(850_000);
        assertThat(IncomeTax.calc(TaxRegime.OLD, 2025, income, BigDecimal.ZERO, null, 12, 45).annualTax()).isEqualByComparingTo("75400");
        assertThat(IncomeTax.calc(TaxRegime.OLD, 2025, income, BigDecimal.ZERO, null, 12, 65).annualTax()).isEqualByComparingTo("72800");
        IncomeTax.Calc superSenior = IncomeTax.calc(TaxRegime.OLD, 2025, income, BigDecimal.ZERO, null, 12, 85);
        assertThat(superSenior.annualTax()).isEqualByComparingTo("62400");
        assertThat(superSenior.rules()).contains("super senior");
        // the new regime is the same at any age
        assertThat(IncomeTax.calc(TaxRegime.NEW, 2025, BigDecimal.valueOf(1_375_000), BigDecimal.ZERO, null, 12, 70).annualTax())
                .isEqualByComparingTo("78000");
    }

    @Test
    void aYearAfterTheLatestRulesSaysSo() {
        assertThat(newer(2025, 1_375_000).rulesFromEarlierYear()).isFalse();
        IncomeTax.Calc later = newer(2026, 1_375_000);
        assertThat(later.rulesFromEarlierYear()).isTrue();
        assertThat(later.rules()).contains("check FY 2026-27");
        assertThat(later.annualTax()).isEqualByComparingTo("78000");
    }
}
