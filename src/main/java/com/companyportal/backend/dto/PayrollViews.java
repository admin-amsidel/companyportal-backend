/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.dto;

import com.companyportal.backend.entity.Employee;
import com.companyportal.backend.entity.PayComponent;
import com.companyportal.backend.entity.PayComponentKind;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** What the payroll screens show. Money in rupees with 2 decimals. */
public final class PayrollViews {

    private PayrollViews() {
    }

    public record ComponentView(Long id, String code, String name, PayComponentKind kind, int sortOrder, boolean active,
                                boolean tax, boolean inUse) {
        public static ComponentView of(PayComponent c, boolean inUse) {
            return new ComponentView(c.getId(), c.getCode(), c.getName(), c.getKind(), c.getSortOrder(), c.isActive(),
                    c.isTax(), inUse);
        }
    }

    /** pan and bankAccount are payroll details: only payroll users and the person see them. */
    public record Person(Long id, String name, String empNumber, String designationName, String brandCode, String brandName,
                         LocalDate joinedDate, boolean active, String photoPath, Instant photoUpdatedAt, String pan,
                         String bankAccount, String ifsc, String upi, LocalDate exitDate) {
        public static Person of(Employee e) {
            return new Person(e.getId(), e.getName(), e.getEmpNumber(),
                    e.getDesignation() == null ? null : e.getDesignation().getName(),
                    e.getBrand() == null ? null : e.getBrand().getCode(), e.getBrand() == null ? null : e.getBrand().getName(),
                    e.getJoinedDate(), e.isActive(),
                    e.getPhotoPath(), e.getPhotoUpdatedAt(), e.getPanNumber(), e.getBankAccountNumber(), e.getIfscCode(),
                    e.getUpiId(), e.getExitDate());
        }
    }

    /**
     * Totals of a salary. monthly*: one full month at the revision shown (EPF, ESI, professional tax and income tax as
     * worked out); annual*: monthly x 12 (the yearly package); year*: the financial year month by month (payslips as made,
     * the months to come worked out; months paid outside the portal not included), over monthsInYear months employed.
     */
    public record Totals(BigDecimal monthlyGross, BigDecimal monthlyDeductions, BigDecimal monthlyReimbursements,
                         BigDecimal monthlyNet, BigDecimal annualGross, BigDecimal annualDeductions,
                         BigDecimal annualReimbursements, BigDecimal annualNet, int monthsInYear, BigDecimal yearGross,
                         BigDecimal yearNet) {
    }

    /** auto: worked out by payroll (EPF, ESI, professional tax, income tax), not typed. */
    public record LineView(Long componentId, String code, String name, PayComponentKind kind, boolean active,
                           boolean tax, BigDecimal monthly, BigDecimal annual, boolean auto) {
    }

    /** One salary revision of the year: from its date until the day before the next (null = the year's end). */
    public record RevisionView(Long id, LocalDate effectiveFrom, LocalDate until, BigDecimal monthlyGross) {
    }

    /** One month of the year: PAYSLIP (as made), OUTSIDE (paid outside the portal), OPEN (worked out) or NONE (not employed). */
    public record PlanMonthView(int year, int month, String label, String source, BigDecimal gross, BigDecimal deductions,
                                BigDecimal reimbursements, BigDecimal net, BigDecimal incomeTax, BigDecimal professionalTax) {
    }

    /** A professional tax slab: a half year's gross pay from fromIncome up pays halfYearTax. */
    public record PtSlabView(BigDecimal fromIncome, BigDecimal halfYearTax) {
    }

    /** One person in the payroll list; set = false when no salary is entered for this year yet. */
    /** effectiveFrom / workingDays / totals: the latest revision's; revisions: how many the year has. */
    public record SalaryRow(Person employee, boolean set, LocalDate effectiveFrom, int workingDays, Totals totals, boolean editable,
                            String updatedByName, Instant updatedAt, com.companyportal.backend.entity.TaxRegime taxRegime, int revisions) {
    }

    /**
     * One revision of a person's salary for a year (revisionId; null = none yet), with a line for every component
     * (active ones, plus any retired one it uses), the year's revisions, the statutory deductions it takes, the income
     * and tax paid this year outside the portal, and the year month by month.
     */
    public record SalaryView(Person employee, int fyStartYear, String fyLabel, LocalDate fyStart, LocalDate fyEnd,
                             boolean set, LocalDate effectiveFrom, int workingDays, String notes, List<LineView> lines, Totals totals,
                             boolean editable, String updatedByName, Instant updatedAt, TaxView tax, Long revisionId,
                             List<RevisionView> revisions, boolean pfApplies, boolean pfFullWage, boolean esiApplies, boolean ptApplies,
                             BigDecimal outsideIncome, BigDecimal outsideTax, List<PlanMonthView> plan) {
    }

    /**
     * The salary's income tax: the regime, the old-regime declaration, the tax worked out under the regime chosen (null
     * for manual), both regimes side by side to compare, and whether I may type it by hand.
     */
    /** taxBefore: deducted already (outside the portal and on payslips); each calc's monthlyTax is what is left over its months. */
    public record TaxView(com.companyportal.backend.entity.TaxRegime regime, BigDecimal declared, com.companyportal.backend.service.IncomeTax.Calc chosen,
                          com.companyportal.backend.service.IncomeTax.Calc newRegime, com.companyportal.backend.service.IncomeTax.Calc oldRegime,
                          boolean canManual, boolean canChooseRegime, String taxComponentName, BigDecimal taxBefore) {
    }

    /** The payroll list for a year, with what it all costs. */
    public record YearView(int fyStartYear, String fyLabel, List<Integer> years, List<SalaryRow> rows, Totals totals,
                           int withSalary) {
    }
}
