/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.service;

import com.companyportal.backend.entity.*;
import com.companyportal.backend.repository.PayComponentRepository;
import com.companyportal.backend.repository.ProfessionalTaxSlabRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.time.YearMonth;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The one place a month's pay is worked out: payslips, the salary screen and My pay all use it, so they always agree.
 * <p>
 * <b>Days.</b> A day's pay is the salary's monthly amount / its working days a month (30 unless set otherwise). A month
 * has as many days payable as the salary's working days; payroll may lower them on a payslip, and fewer days pay
 * proportionally less (PORTAL-20). A whole month employed is all of them. A month the salary starts or the person leaves in counts the calendar days employed,
 * weekly offs included, scaled to the working days: joining on 9 November = 22 of 30, leaving on 15 January = 15 x 30 /
 * 31 = 15. Paid days = employed days - loss-of-pay days (half days allowed).
 * <p>
 * <b>Revisions.</b> A year may have several salaries, each from its own date. A month with a change is paid at each
 * one for its share of the month's days employed.
 * <p>
 * <b>Lines.</b> Earnings and the fixed deductions are paid for the paid days; reimbursements in full; an income tax typed
 * in (Manual) as typed. Worked out when the salary says so: EPF (12% of the Basic + DA paid, on at most ₹15,000 unless on
 * the full wage), ESI (0.75% of the gross paid, rounded up, while the monthly gross is ₹21,000 or less), professional tax
 * (the half year's tax from the slabs on the half year's gross, less what was deducted in it, over its months left) and
 * income tax (the year's tax on the year's income, less what was deducted, over the months left).
 * <p>
 * <b>The year.</b> Each month is one of: a payslip (as made); a month before "now" with no payslip (paid outside the
 * portal: it counts nothing, the tax year's outside income and tax stand for it); or open (worked out, from now on).
 * Professional tax and income tax are spread evenly over the open months.
 */
@Service
@RequiredArgsConstructor
public class PayEngine {

    static final String BASIC = "BASIC";
    static final String DA = "DA";
    static final String EPF = "EPF";
    static final String ESI = "ESI";
    static final String PT = "PT";
    static final String TDS = "TDS";

    static final BigDecimal PF_RATE = new BigDecimal("0.12");
    static final BigDecimal PF_WAGE_CEILING = new BigDecimal("15000");
    static final BigDecimal ESI_RATE = new BigDecimal("0.0075");
    static final BigDecimal ESI_GROSS_LIMIT = new BigDecimal("21000");

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final PayComponentRepository componentRepository;
    private final ProfessionalTaxSlabRepository slabRepository;

    // ---- what goes in and what comes out ----

    /** Everything about one person's year that pay depends on. */
    public record Context(Employee employee, int fy, List<SalaryStructure> revisions, TaxRegime regime, BigDecimal declared,
                          BigDecimal outsideIncome, BigDecimal outsideTax, List<Payslip> payslips, List<ProfessionalTaxSlab> slabs,
                          Map<String, PayComponent> components) {

        /** The same year with other revisions or another regime (to compare, or for a salary being typed). */
        public Context with(List<SalaryStructure> otherRevisions, TaxRegime otherRegime, BigDecimal otherDeclared,
                            BigDecimal otherOutsideIncome, BigDecimal otherOutsideTax) {
            return new Context(employee, fy, otherRevisions, otherRegime, otherDeclared, otherOutsideIncome, otherOutsideTax, payslips,
                    slabs, components);
        }
    }

    /** One line of a month's pay. auto: worked out by payroll (EPF, ESI, professional tax, income tax). */
    public record Line(PayComponent component, BigDecimal fullAmount, BigDecimal amount, boolean auto) {
    }

    public record MonthPay(YearMonth month, int workingDays, int employedDays, BigDecimal lopDays, BigDecimal paidDays,
                           SalaryStructure revision, List<Line> lines) {

        public BigDecimal total(PayComponentKind kind) {
            return lines.stream().filter(l -> l.component().getKind() == kind).map(Line::amount).reduce(ZERO, BigDecimal::add);
        }

        public BigDecimal gross() {
            return total(PayComponentKind.EARNING);
        }

        public BigDecimal net() {
            return gross().subtract(total(PayComponentKind.DEDUCTION)).add(total(PayComponentKind.REIMBURSEMENT));
        }

        public BigDecimal amountOf(String code) {
            return lines.stream().filter(l -> code.equalsIgnoreCase(l.component().getCode())).map(Line::amount).reduce(ZERO, BigDecimal::add);
        }

        boolean employed() {
            return employedDays > 0;
        }
    }

    /**
     * How the income tax was worked out: the year's income and tax, what was deducted before (outside the portal and on
     * other payslips), and what is left spread over the open months. Null calc for a Manual tax year.
     */
    public record TaxWork(TaxRegime regime, IncomeTax.Calc calc, BigDecimal taxBefore, int openMonths, BigDecimal monthTax) {
    }

    /** PAYSLIP: as made; OUTSIDE: before now with no payslip (paid outside the portal); OPEN: worked out; NONE: not employed. */
    public enum Source { PAYSLIP, OUTSIDE, OPEN, NONE }

    public record PlanMonth(YearMonth month, Source source, BigDecimal gross, BigDecimal deductions, BigDecimal reimbursements,
                            BigDecimal net, BigDecimal incomeTax, BigDecimal professionalTax) {
    }

    /** The year month by month, its totals, and the income tax. */
    public record YearPlan(List<PlanMonth> months, Map<YearMonth, MonthPay> open, TaxWork tax, BigDecimal gross, BigDecimal net,
                           BigDecimal incomeTax, int monthsEmployed) {
    }

    /** A month worked out for a payslip, with the income tax behind it. */
    public record MonthResult(MonthPay pay, TaxWork tax) {
    }

    // ---- building the context ----

    public Context context(Employee e, int fy, List<SalaryStructure> revisions, PayrollTaxYear taxYear, List<Payslip> payslips) {
        Map<String, PayComponent> components = componentRepository.findAll().stream()
                .collect(Collectors.toMap(c -> c.getCode().toUpperCase(Locale.ROOT), Function.identity(), (a, b) -> a));
        List<SalaryStructure> sorted = revisions.stream().sorted(Comparator.comparing(SalaryStructure::getEffectiveFrom)).toList();
        return new Context(e, fy, sorted, taxYear == null ? TaxRegime.MANUAL : taxYear.getTaxRegime(),
                taxYear == null ? null : taxYear.getTaxDeclared(), taxYear == null ? null : taxYear.getOutsideIncome(),
                taxYear == null ? null : taxYear.getOutsideTax(), payslips, slabRepository.ordered(), components);
    }

    // ---- days ----

    /** The days of the month the person is paid for (both included); null when not employed in it. */
    record Span(LocalDate from, LocalDate to) {
    }

    static Span span(Context c, YearMonth ym) {
        return span(c.employee(), c.revisions(), ym);
    }

    /** From the later of the month's start, the first revision and the joining date; to the earlier of its end and the exit date. */
    static Span span(Employee e, List<SalaryStructure> revisions, YearMonth ym) {
        if (revisions == null || revisions.isEmpty()) {
            return null;
        }
        LocalDate first = revisions.stream().map(SalaryStructure::getEffectiveFrom).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate from = max(ym.atDay(1), first);
        if (e.getJoinedDate() != null) {
            from = max(from, e.getJoinedDate());
        }
        LocalDate to = ym.atEndOfMonth();
        if (e.getExitDate() != null && e.getExitDate().isBefore(to)) {
            to = e.getExitDate();
        }
        return from.isAfter(to) ? null : new Span(from, to);
    }

    /** Whether the person is paid for any day of the month: a salary has started, and they have not left before it. */
    public static boolean employedIn(Employee e, List<SalaryStructure> revisions, YearMonth ym) {
        return span(e, revisions, ym) != null;
    }

    /** All the working days for a whole month employed; else the calendar days employed scaled to the working days. */
    static int employedDays(Span span, YearMonth ym, int workingDays) {
        if (span == null) {
            return 0;
        }
        if (!span.from().isAfter(ym.atDay(1)) && !span.to().isBefore(ym.atEndOfMonth())) {
            return workingDays;
        }
        long days = span.to().toEpochDay() - span.from().toEpochDay() + 1;
        int scaled = BigDecimal.valueOf(days * workingDays).divide(BigDecimal.valueOf(ym.lengthOfMonth()), 0, RoundingMode.HALF_UP).intValue();
        return Math.min(workingDays, scaled);
    }

    /** The revision in force on a day: the latest that has started; null before the first. */
    static SalaryStructure inForce(List<SalaryStructure> revisions, LocalDate day) {
        SalaryStructure found = null;
        for (SalaryStructure r : revisions) {
            if (!r.getEffectiveFrom().isAfter(day)) {
                found = r;
            }
        }
        return found;
    }

    /** The working days a month has by default: those of the revision in force at its end (or its last day employed). */
    public int workingDaysOf(Context c, YearMonth ym) {
        Span s = span(c, ym);
        SalaryStructure r = inForce(c.revisions(), s != null ? s.to() : ym.atEndOfMonth());
        if (r == null && !c.revisions().isEmpty()) {
            r = c.revisions().getFirst();
        }
        return r == null ? PayrollService.DEFAULT_WORKING_DAYS : r.getWorkingDays();
    }

    // ---- one month, before professional tax and income tax ----

    /**
     * The month's earnings, fixed deductions, reimbursements, EPF and ESI. typed: amounts payroll typed, by component
     * id, used for every line payroll does not work out itself.
     */
    MonthPay base(Context c, YearMonth ym, int workingDays, BigDecimal lop, Map<Long, BigDecimal> typed) {
        Span span = span(c, ym);
        if (span == null) {
            return new MonthPay(ym, workingDays, 0, BigDecimal.ZERO.setScale(1), BigDecimal.ZERO.setScale(1), null, List.of());
        }
        int employed = employedDays(span, ym, workingDays);
        BigDecimal lopDays = nz(lop).max(BigDecimal.ZERO).min(BigDecimal.valueOf(employed)).setScale(1, RoundingMode.HALF_UP);
        BigDecimal paid = BigDecimal.valueOf(employed).subtract(lopDays).setScale(1, RoundingMode.HALF_UP);

        // each revision's share of the days employed this month
        List<SalaryStructure> revs = c.revisions();
        Map<SalaryStructure, Long> share = new LinkedHashMap<>();
        for (int i = 0; i < revs.size(); i++) {
            SalaryStructure r = revs.get(i);
            LocalDate from = max(r.getEffectiveFrom(), span.from());
            LocalDate to = i + 1 < revs.size() ? min(revs.get(i + 1).getEffectiveFrom().minusDays(1), span.to()) : span.to();
            if (!from.isAfter(to)) {
                share.put(r, to.toEpochDay() - from.toEpochDay() + 1);
            }
        }
        long total = share.values().stream().mapToLong(Long::longValue).sum();
        SalaryStructure latest = inForce(revs, span.to());
        if (latest == null) {
            latest = revs.getFirst();
        }
        Map<Long, PayComponent> components = new HashMap<>();
        Map<Long, BigDecimal> weighted = new HashMap<>();
        for (Map.Entry<SalaryStructure, Long> e : share.entrySet()) {
            for (SalaryLine l : e.getKey().getLines()) {
                components.put(l.getComponent().getId(), l.getComponent());
                weighted.merge(l.getComponent().getId(), l.getMonthlyAmount().multiply(BigDecimal.valueOf(e.getValue()))
                        .divide(BigDecimal.valueOf(total), 6, RoundingMode.HALF_UP), BigDecimal::add);
            }
        }
        Map<Long, BigDecimal> latestAmount = latest.getLines().stream()
                .collect(Collectors.toMap(l -> l.getComponent().getId(), SalaryLine::getMonthlyAmount, BigDecimal::add));
        boolean blended = share.size() > 1;
        // PORTAL-20: a day's pay is always the salary's own (monthly / its working days a month); the month's days only
        // say how many days are payable, so fewer of them pay less
        int basis = Math.max(1, latest.getWorkingDays());

        List<Line> lines = new ArrayList<>();
        for (PayComponent comp : components.values()) {
            String code = comp.getCode().toUpperCase(Locale.ROOT);
            if (isAuto(code, latest, c.regime())) {
                continue;
            }
            BigDecimal monthly = weighted.get(comp.getId());
            if (blended) {
                monthly = monthly.setScale(0, RoundingMode.HALF_UP);
            }
            BigDecimal full = latestAmount.getOrDefault(comp.getId(), BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            BigDecimal amount = switch (comp.getKind()) {
                case EARNING -> forDays(monthly, paid, basis);
                case DEDUCTION -> TDS.equals(code) ? full : forDays(monthly, paid, basis);
                case REIMBURSEMENT -> full;
            };
            if (typed != null && typed.containsKey(comp.getId())) {
                amount = typed.get(comp.getId()).setScale(2, RoundingMode.HALF_UP);
            }
            if (comp.getKind() == PayComponentKind.REIMBURSEMENT && full.signum() == 0 && amount.signum() == 0) {
                continue;
            }
            lines.add(new Line(comp, full, amount, false));
        }

        BigDecimal gross = lines.stream().filter(l -> l.component().getKind() == PayComponentKind.EARNING).map(Line::amount)
                .reduce(ZERO, BigDecimal::add);
        PayComponent epf = c.components().get(EPF);
        if (latest.isPfApplies() && epf != null) {
            BigDecimal wage = lines.stream().filter(l -> isPfWage(l.component())).map(Line::amount).reduce(ZERO, BigDecimal::add);
            BigDecimal fullWage = latest.getLines().stream().filter(l -> isPfWage(l.getComponent())).map(SalaryLine::getMonthlyAmount)
                    .reduce(ZERO, BigDecimal::add);
            lines.add(new Line(epf, pf(fullWage, latest), pf(wage, latest), true));
        }
        PayComponent esi = c.components().get(ESI);
        BigDecimal fullGross = latest.getLines().stream().filter(l -> l.getComponent().getKind() == PayComponentKind.EARNING)
                .map(SalaryLine::getMonthlyAmount).reduce(ZERO, BigDecimal::add);
        if (latest.isEsiApplies() && esi != null && fullGross.compareTo(ESI_GROSS_LIMIT) <= 0) {
            lines.add(new Line(esi, esi(fullGross), esi(gross), true));
        }
        return new MonthPay(ym, workingDays, employed, lopDays, paid, latest, sorted(lines));
    }

    /** EPF, ESI and professional tax are worked out when the salary says so; income tax unless typed in (Manual). */
    static boolean isAuto(String code, SalaryStructure latest, TaxRegime regime) {
        return switch (code) {
            case EPF -> latest.isPfApplies();
            case ESI -> latest.isEsiApplies();
            case PT -> latest.isPtApplies();
            case TDS -> regime != TaxRegime.MANUAL;
            default -> false;
        };
    }

    private static boolean isPfWage(PayComponent c) {
        String code = c.getCode().toUpperCase(Locale.ROOT);
        return c.getKind() == PayComponentKind.EARNING && (BASIC.equals(code) || DA.equals(code));
    }

    static BigDecimal pf(BigDecimal wage, SalaryStructure s) {
        BigDecimal base = s.isPfFullWage() ? wage : wage.min(PF_WAGE_CEILING);
        return base.multiply(PF_RATE).setScale(0, RoundingMode.HALF_UP).setScale(2);
    }

    /** ESI is rounded up to the next rupee. */
    static BigDecimal esi(BigDecimal gross) {
        return gross.multiply(ESI_RATE).setScale(0, RoundingMode.CEILING).setScale(2);
    }

    /** Pay for the paid days: monthly / working days x paid days, to the rupee (the full amount for a full month). */
    static BigDecimal forDays(BigDecimal monthly, BigDecimal paid, int days) {
        if (paid.compareTo(BigDecimal.valueOf(days)) >= 0) {
            return monthly.setScale(2, RoundingMode.HALF_UP);
        }
        return monthly.multiply(paid).divide(BigDecimal.valueOf(days), 0, RoundingMode.HALF_UP).setScale(2);
    }

    // ---- the year: professional tax and income tax over the open months ----

    /** A payslip's month (its id left out of the payslips counted), worked out with these days and typed amounts. */
    public MonthResult month(Context c, YearMonth ym, int workingDays, BigDecimal lop, Map<Long, BigDecimal> typed, Long payslipId) {
        MonthPay base = base(c, ym, workingDays, lop, typed);
        Solved s = solve(c, ym, payslipId, base);
        return new MonthResult(s.open().getOrDefault(ym, base), s.tax());
    }

    /**
     * The year as the next payslip would see it: from the month after the year's latest payslip (or its first month when
     * there is none) on, the months are worked out; earlier months without a payslip were paid outside the portal.
     */
    public YearPlan plan(Context c) {
        YearMonth from = YearMonth.of(c.fy(), 4);
        for (Payslip p : c.payslips()) {
            YearMonth next = YearMonth.of(p.getPeriodYear(), p.getPeriodMonth()).plusMonths(1);
            if (next.isAfter(from)) {
                from = next;
            }
        }
        return plan(c, from);
    }

    /** The year as of a month: payslips as made, earlier months without one as paid outside, the rest worked out. */
    public YearPlan plan(Context c, YearMonth asOf) {
        Solved s = solve(c, asOf, null, null);
        Map<YearMonth, Payslip> made = s.made();
        List<PlanMonth> months = new ArrayList<>();
        BigDecimal gross = ZERO;
        BigDecimal net = ZERO;
        BigDecimal incomeTax = c.outsideTax() == null ? ZERO : c.outsideTax().setScale(2, RoundingMode.HALF_UP);
        int employed = 0;
        for (YearMonth m : months(c.fy())) {
            Payslip p = made.get(m);
            MonthPay o = s.open().get(m);
            PlanMonth pm;
            if (p != null) {
                pm = fromPayslip(m, p);
            } else if (o != null) {
                pm = new PlanMonth(m, Source.OPEN, o.gross(), o.total(PayComponentKind.DEDUCTION), o.total(PayComponentKind.REIMBURSEMENT),
                        o.net(), o.amountOf(TDS), o.amountOf(PT));
            } else if (span(c, m) != null) {
                pm = new PlanMonth(m, Source.OUTSIDE, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO);
            } else {
                pm = new PlanMonth(m, Source.NONE, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO);
            }
            if (pm.source() != Source.NONE) {
                employed++;
            }
            gross = gross.add(pm.gross());
            net = net.add(pm.net());
            if (p != null || o != null) {
                incomeTax = incomeTax.add(pm.incomeTax());
            }
            months.add(pm);
        }
        return new YearPlan(months, s.open(), s.tax(), gross, net, incomeTax, employed);
    }

    private record Solved(Map<YearMonth, Payslip> made, TreeMap<YearMonth, MonthPay> open, TaxWork tax) {
    }

    /**
     * The open months from asOf on (the one being worked out, when given, with its own days and amounts) with their
     * professional tax and income tax.
     */
    private Solved solve(Context c, YearMonth asOf, Long leaveOut, MonthPay computing) {
        Map<YearMonth, Payslip> made = new HashMap<>();
        for (Payslip p : c.payslips()) {
            if (!Objects.equals(p.getId(), leaveOut)) {
                made.put(YearMonth.of(p.getPeriodYear(), p.getPeriodMonth()), p);
            }
        }
        TreeMap<YearMonth, MonthPay> open = new TreeMap<>();
        if (computing != null && computing.employed()) {
            open.put(computing.month(), computing);
        }
        for (YearMonth m : months(c.fy())) {
            if (made.containsKey(m) || open.containsKey(m) || m.isBefore(asOf) || (computing != null && m.equals(computing.month()))) {
                continue;
            }
            MonthPay b = base(c, m, workingDaysOf(c, m), BigDecimal.ZERO, null);
            if (b.employed()) {
                open.put(m, b);
            }
        }

        // professional tax: per half year, spread over its open months the salary takes it in
        PayComponent pt = c.components().get(PT);
        if (pt != null) {
            for (int half = 0; half < 2; half++) {
                final int h = half;
                List<YearMonth> taking = open.keySet().stream().filter(m -> half(m) == h && open.get(m).revision() != null
                        && open.get(m).revision().isPtApplies()).toList();
                if (taking.isEmpty()) {
                    continue;
                }
                BigDecimal income = ZERO;
                BigDecimal before = ZERO;
                for (Map.Entry<YearMonth, Payslip> e : made.entrySet()) {
                    if (fyOf(e.getKey()) == c.fy() && half(e.getKey()) == h) {
                        income = income.add(sum(e.getValue(), PayComponentKind.EARNING, null));
                        before = before.add(sum(e.getValue(), PayComponentKind.DEDUCTION, PT));
                    }
                }
                for (Map.Entry<YearMonth, MonthPay> e : open.entrySet()) {
                    if (half(e.getKey()) == h) {
                        income = income.add(e.getValue().gross());
                    }
                }
                BigDecimal each = halfYearTax(c.slabs(), income).subtract(before).max(BigDecimal.ZERO)
                        .divide(BigDecimal.valueOf(taking.size()), 0, RoundingMode.HALF_UP).setScale(2);
                for (YearMonth m : taking) {
                    open.put(m, add(open.get(m), new Line(pt, each, each, true)));
                }
            }
        }

        // income tax: the year's tax, less what was deducted, over the open months
        TaxWork tax = null;
        PayComponent tds = c.components().get(TDS);
        if (c.regime() != TaxRegime.MANUAL) {
            BigDecimal income = nz(c.outsideIncome());
            BigDecimal ptPaid = ZERO;
            BigDecimal before = nz(c.outsideTax());
            for (Payslip p : made.values()) {
                income = income.add(sum(p, PayComponentKind.EARNING, null));
                ptPaid = ptPaid.add(sum(p, PayComponentKind.DEDUCTION, PT));
                before = before.add(sum(p, PayComponentKind.DEDUCTION, TDS));
            }
            for (MonthPay m : open.values()) {
                income = income.add(m.gross());
                ptPaid = ptPaid.add(m.amountOf(PT));
            }
            IncomeTax.Calc year = IncomeTax.calc(c.regime(), c.fy(), income, ptPaid, c.declared(), Math.max(open.size(), 1), age(c));
            BigDecimal each = open.isEmpty() ? ZERO : year.annualTax().subtract(before).max(BigDecimal.ZERO)
                    .divide(BigDecimal.valueOf(open.size()), 0, RoundingMode.HALF_UP).setScale(2);
            IncomeTax.Calc shown = new IncomeTax.Calc(year.regime(), year.rules(), year.income(), year.standardDeduction(), year.professionalTax(),
                    year.declared(), year.taxable(), year.slabTax(), year.rebate(), year.surcharge(), year.cess(), year.annualTax(),
                    open.size(), each, year.rulesFromEarlierYear());
            tax = new TaxWork(c.regime(), shown, before.setScale(2, RoundingMode.HALF_UP), open.size(), each);
            if (tds != null) {
                for (YearMonth m : open.keySet()) {
                    open.put(m, add(open.get(m), new Line(tds, each, each, true)));
                }
            }
        }
        return new Solved(made, open, tax);
    }

    /** Age on the year's last day (31 March); 0 when the date of birth is not known. */
    static int age(Context c) {
        LocalDate dob = c.employee().getDob();
        return dob == null ? 0 : Math.max(0, Period.between(dob, LocalDate.of(c.fy() + 1, 3, 31)).getYears());
    }

    /** The tax of the highest slab the half year's income reaches. */
    static BigDecimal halfYearTax(List<ProfessionalTaxSlab> slabs, BigDecimal income) {
        BigDecimal tax = ZERO;
        for (ProfessionalTaxSlab s : slabs) {
            if (income.compareTo(s.getFromIncome()) >= 0) {
                tax = s.getHalfYearTax().setScale(2, RoundingMode.HALF_UP);
            }
        }
        return tax;
    }

    /** 0 = April to September, 1 = October to March. */
    static int half(YearMonth m) {
        return m.getMonthValue() >= 4 && m.getMonthValue() <= 9 ? 0 : 1;
    }

    static List<YearMonth> months(int fy) {
        List<YearMonth> out = new ArrayList<>(12);
        for (int i = 0; i < 12; i++) {
            out.add(YearMonth.of(fy, 4).plusMonths(i));
        }
        return out;
    }

    static int fyOf(YearMonth ym) {
        return ym.getMonthValue() >= 4 ? ym.getYear() : ym.getYear() - 1;
    }

    private static PlanMonth fromPayslip(YearMonth m, Payslip p) {
        BigDecimal gross = sum(p, PayComponentKind.EARNING, null);
        BigDecimal deductions = sum(p, PayComponentKind.DEDUCTION, null);
        BigDecimal reimbursements = sum(p, PayComponentKind.REIMBURSEMENT, null);
        return new PlanMonth(m, Source.PAYSLIP, gross, deductions, reimbursements, gross.subtract(deductions).add(reimbursements),
                sum(p, PayComponentKind.DEDUCTION, TDS), sum(p, PayComponentKind.DEDUCTION, PT));
    }

    /** A payslip's total of one kind, or of one component code of it. */
    private static BigDecimal sum(Payslip p, PayComponentKind kind, String code) {
        return p.getLines().stream().filter(l -> l.getKind() == kind && (code == null || code.equalsIgnoreCase(l.getCode())))
                .map(PayslipLine::getAmount).reduce(ZERO, BigDecimal::add);
    }

    private static MonthPay add(MonthPay m, Line line) {
        List<Line> lines = new ArrayList<>(m.lines());
        lines.add(line);
        return new MonthPay(m.month(), m.workingDays(), m.employedDays(), m.lopDays(), m.paidDays(), m.revision(), sorted(lines));
    }

    /** Earnings, then deductions, then reimbursements; in the component order within each. */
    private static List<Line> sorted(List<Line> lines) {
        return lines.stream().sorted(Comparator.comparingInt((Line l) -> l.component().getKind().ordinal())
                .thenComparingInt(l -> l.component().getSortOrder()).thenComparing(l -> l.component().getCode())).toList();
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? ZERO : v;
    }
}
