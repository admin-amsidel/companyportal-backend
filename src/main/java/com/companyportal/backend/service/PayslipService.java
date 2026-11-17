/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.service;

import com.companyportal.backend.dto.PayrollViews.Person;
import com.companyportal.backend.dto.PayrollViews.PlanMonthView;
import com.companyportal.backend.dto.PayrollViews.SalaryView;
import com.companyportal.backend.dto.PayslipDtos.*;
import com.companyportal.backend.entity.*;
import com.companyportal.backend.exception.ApiException;
import com.companyportal.backend.exception.ResourceNotFoundException;
import com.companyportal.backend.repository.EmployeeRepository;
import com.companyportal.backend.repository.PayrollTaxYearRepository;
import com.companyportal.backend.repository.PayslipRepository;
import com.companyportal.backend.repository.SalaryStructureRepository;
import com.companyportal.backend.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Monthly payslips made from each employee's salary for the year. The amounts are worked out by PayEngine (days,
 * revisions, EPF, ESI, professional tax and income tax); a new draft takes the month's approved unpaid leave as its
 * loss-of-pay days. Payroll can change the days and any amount while it is a DRAFT (what payroll works out follows),
 * then publishes it and records the salary as credited (when, bank / cash / UPI, reference). The employee sees a payslip
 * only when it is published and credited.
 * Who sees whose: as for salaries (PayrollAccess).
 */
@Service
@RequiredArgsConstructor
public class PayslipService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final BigDecimal HALF = new BigDecimal("0.5");

    private final PayslipRepository payslipRepository;
    private final SalaryStructureRepository salaryRepository;
    private final PayrollTaxYearRepository taxYearRepository;
    private final EmployeeRepository employeeRepository;
    private final PayrollAccess access;
    private final PayrollService payrollService;
    private final TeamAccess teamAccess;
    private final PayEngine engine;
    private final LeaveService leaveService;

    @Value("${app.leave.zone:Asia/Kolkata}")
    private String zone;

    @Value("${app.payslip.company-name:My Company}")
    private String companyName;

    @Value("${app.payslip.company-address:}")
    private String companyAddress;

    // ---- payroll: the month ----

    @Transactional(readOnly = true)
    public MonthView month(AuthUser me, int year, int month) {
        YearMonth ym = yearMonth(year, month);
        int fy = fyOf(ym);
        Map<Long, List<SalaryStructure>> salaries = salaryRepository.ofYear(fy).stream()
                .collect(Collectors.groupingBy(s -> s.getEmployee().getId()));
        Map<Long, Payslip> payslips = payslipRepository.ofMonth(year, month).stream()
                .collect(Collectors.toMap(p -> p.getEmployee().getId(), Function.identity()));
        List<Row> rows = new ArrayList<>();
        List<Money> made = new ArrayList<>();
        int eligible = 0;
        int drafts = 0;
        int published = 0;
        int credited = 0;
        for (Employee e : access.people(me)) {
            List<SalaryStructure> revs = salaries.get(e.getId());
            Payslip p = payslips.get(e.getId());
            boolean salarySet = PayEngine.employedIn(e, revs, ym);
            if (p == null && (!payable(e) || !salarySet)) {
                if (e.isActive()) {
                    rows.add(new Row(Person.of(e), false, null, null, 0, BigDecimal.ZERO, BigDecimal.ZERO, money(List.of()),
                            access.editable(me, e), null, null));
                }
                continue;
            }
            eligible++;
            if (p == null) {
                rows.add(new Row(Person.of(e), true, null, null, revs.getLast().getWorkingDays(), BigDecimal.ZERO, BigDecimal.ZERO,
                        money(List.of()), access.editable(me, e), null, null));
                continue;
            }
            Money m = money(p.getLines());
            made.add(m);
            if (p.getStatus() == PayslipStatus.DRAFT) {
                drafts++;
            } else {
                published++;
                if (p.getPaidOn() != null) {
                    credited++;
                }
            }
            rows.add(new Row(Person.of(e), salarySet, p.getId(), p.getStatus(), p.getWorkingDays(), p.getPaidDays(), p.getLopDays(), m,
                    access.editable(me, e), p.getPaidOn(), p.getPaymentMode()));
        }
        return new MonthView(year, month, label(ym), fy, rows, eligible, drafts, published, credited, sum(made));
    }

    /** Draft payslips for everyone (or these people) paid for any day of the month, with no payslip yet. */
    @Transactional
    public GenerateResult generate(AuthUser me, Generate body) {
        YearMonth ym = yearMonth(body.getYear(), body.getMonth());
        int fy = fyOf(ym);
        Set<Long> only = body.getEmployeeIds() == null || body.getEmployeeIds().isEmpty() ? null : Set.copyOf(body.getEmployeeIds());
        Map<Long, List<SalaryStructure>> salaries = salaryRepository.ofYear(fy).stream()
                .collect(Collectors.groupingBy(s -> s.getEmployee().getId()));
        Employee by = employeeRepository.getReferenceById(me.id());
        int created = 0;
        int skipped = 0;
        List<String> notes = new ArrayList<>();
        for (Employee e : access.people(me)) {
            if (only != null && !only.contains(e.getId())) {
                continue;
            }
            List<SalaryStructure> revs = salaries.get(e.getId());
            if (!payable(e) || !PayEngine.employedIn(e, revs, ym)) {
                if (only != null) {
                    skipped++;
                    notes.add(e.getName() + ": no salary for " + label(ym));
                }
                continue;
            }
            if (!access.editable(me, e)) {
                skipped++;
                notes.add(e.getName() + ": your own payslip is made by someone else");
                continue;
            }
            if (payslipRepository.existsByEmployee_IdAndPeriodYearAndPeriodMonth(e.getId(), ym.getYear(), ym.getMonthValue())) {
                skipped++;
                continue;
            }
            PayEngine.Context ctx = context(e, fy);
            int working = body.getWorkingDays() != null ? body.getWorkingDays() : engine.workingDaysOf(ctx, ym);
            BigDecimal unpaid = leaveService.unpaidDays(e, ym.atDay(1), ym.atEndOfMonth());
            PayEngine.MonthPay pay = engine.month(ctx, ym, working, unpaid, null, null).pay();
            Payslip p = new Payslip();
            p.setEmployee(e);
            p.setFyStartYear(fy);
            p.setPeriodYear(ym.getYear());
            p.setPeriodMonth(ym.getMonthValue());
            p.setPayDate(ym.atEndOfMonth());
            p.setDesignationName(e.getDesignation() == null ? null : e.getDesignation().getName());
            p.setUpdatedBy(by);
            p.setUnpaidLeaveDays(unpaid.signum() > 0 ? unpaid : null);
            fill(p, pay);
            payslipRepository.save(p);
            created++;
        }
        return new GenerateResult(created, skipped, notes);
    }

    @Transactional(readOnly = true)
    public PayslipView payslip(AuthUser me, Long id) {
        Payslip p = full(id);
        access.visible(me, p.getEmployee().getId());
        return view(p, access.editable(me, p.getEmployee()), true);
    }

    @Transactional
    public PayslipView save(AuthUser me, Long id, Save body) {
        Payslip p = full(id);
        Employee e = access.visible(me, p.getEmployee().getId());
        access.requireEditable(me, e);
        if (p.getStatus() == PayslipStatus.PUBLISHED) {
            throw ApiException.conflict("This payslip is published; move it back to draft to change it");
        }
        YearMonth ym = YearMonth.of(p.getPeriodYear(), p.getPeriodMonth());
        if (body.getPayDate().isBefore(ym.atDay(1)) || body.getPayDate().isAfter(ym.atEndOfMonth().plusDays(15))) {
            throw ApiException.badRequest("The pay date must be in " + label(ym) + " or within 15 days after it");
        }
        int working = body.getWorkingDays() != null ? body.getWorkingDays() : p.getWorkingDays();
        PayEngine.MonthPay pay = work(p, e, working, body.getLopDays(), typed(p, body.getLines())).pay();
        p.setPayDate(body.getPayDate());
        fill(p, pay);
        if (money(p.getLines()).net().signum() < 0) {
            throw ApiException.badRequest("Deductions are more than the pay; check the amounts");
        }
        p.setUpdatedBy(employeeRepository.getReferenceById(me.id()));
        if (body.isPublish()) {
            publishOne(me, p);
        }
        return view(payslipRepository.saveAndFlush(p), true, true);
    }

    /** The draft as it would be with these days and amounts; nothing is saved. */
    @Transactional(readOnly = true)
    public PayslipPreview preview(AuthUser me, Long id, Preview body) {
        Payslip p = full(id);
        Employee e = access.visible(me, p.getEmployee().getId());
        access.requireEditable(me, e);
        int working = body.getWorkingDays() != null ? body.getWorkingDays() : p.getWorkingDays();
        PayEngine.MonthResult r = work(p, e, working, body.getLopDays(), body.isReset() ? null : typed(p, body.getLines()));
        Map<Long, Long> lineIds = p.getLines().stream().filter(l -> l.getComponent() != null)
                .collect(Collectors.toMap(l -> l.getComponent().getId(), PayslipLine::getId, (a, b) -> a));
        Map<String, BigDecimal> ytd = ytdBefore(p, true);
        List<LineView> lines = r.pay().lines().stream()
                .map(l -> new LineView(lineIds.get(l.component().getId()), l.component().getCode(), l.component().getName(),
                        l.component().getKind(), l.component().isTax(), l.fullAmount(), l.amount(),
                        ytd.getOrDefault(l.component().getCode(), ZERO).add(l.amount()), l.auto()))
                .toList();
        return new PayslipPreview(r.pay().workingDays(), r.pay().employedDays(), r.pay().lopDays(), r.pay().paidDays(), lines,
                money(r.pay()), tax(r.tax(), r.pay()));
    }

    /** Makes a draft again from the current salary (after it was changed), keeping its days. */
    @Transactional
    public PayslipView recalculate(AuthUser me, Long id) {
        Payslip p = full(id);
        Employee e = access.visible(me, p.getEmployee().getId());
        access.requireEditable(me, e);
        if (p.getStatus() == PayslipStatus.PUBLISHED) {
            throw ApiException.conflict("This payslip is published; move it back to draft to change it");
        }
        YearMonth ym = YearMonth.of(p.getPeriodYear(), p.getPeriodMonth());
        PayEngine.MonthPay pay = work(p, e, p.getWorkingDays(), p.getLopDays(), null).pay();
        if (pay.employedDays() == 0) {
            throw ApiException.conflict("There is no salary for " + e.getName() + " in " + label(ym) + " any more");
        }
        fill(p, pay);
        p.setUpdatedBy(employeeRepository.getReferenceById(me.id()));
        return view(payslipRepository.saveAndFlush(p), true, true);
    }

    /** Publishes these drafts (the person's own and other people's out of reach are left out). */
    @Transactional
    public int publish(AuthUser me, List<Long> ids) {
        int done = 0;
        for (Long id : ids) {
            Payslip p = full(id);
            Employee e = access.visible(me, p.getEmployee().getId());
            if (p.getStatus() == PayslipStatus.DRAFT && access.editable(me, e)) {
                publishOne(me, p);
                done++;
            }
        }
        return done;
    }

    /** The salary was credited: the employee now sees the payslip. Only a published payslip can be credited. */
    @Transactional
    public PayslipView markPaid(AuthUser me, Long id, Payment body) {
        Payslip p = full(id);
        Employee e = access.visible(me, p.getEmployee().getId());
        access.requireEditable(me, e);
        credit(me, p, body.getPaidOn(), body.getMode(), body.getReference(), body.getRemarks());
        return view(payslipRepository.saveAndFlush(p), true, true);
    }

    /** Undo a credit recorded by mistake: the employee no longer sees the payslip. */
    @Transactional
    public PayslipView unmarkPaid(AuthUser me, Long id) {
        Payslip p = full(id);
        Employee e = access.visible(me, p.getEmployee().getId());
        access.requireEditable(me, e);
        p.setPaidOn(null);
        p.setPaymentMode(null);
        p.setPaymentRef(null);
        p.setPaymentRemarks(null);
        p.setPaidRecordedBy(null);
        return view(payslipRepository.saveAndFlush(p), true, true);
    }

    /** Credits these published payslips the same day the same way; drafts, credited ones and the person's own are left out. */
    @Transactional
    public int markPaid(AuthUser me, BulkPayment body) {
        int done = 0;
        for (Long id : body.getIds()) {
            Payslip p = full(id);
            Employee e = access.visible(me, p.getEmployee().getId());
            if (p.getStatus() == PayslipStatus.PUBLISHED && p.getPaidOn() == null && access.editable(me, e)) {
                credit(me, p, body.getPaidOn(), body.getMode(), body.getReference(), body.getRemarks());
                done++;
            }
        }
        return done;
    }

    private void credit(AuthUser me, Payslip p, LocalDate paidOn, PaymentMode mode, String reference, String remarks) {
        if (p.getStatus() != PayslipStatus.PUBLISHED) {
            throw ApiException.conflict("Publish the payslip first, then record the salary as credited");
        }
        YearMonth ym = YearMonth.of(p.getPeriodYear(), p.getPeriodMonth());
        if (paidOn.isBefore(ym.atDay(1))) {
            throw ApiException.badRequest("The credit date cannot be before " + label(ym));
        }
        if (paidOn.isAfter(LocalDate.now(ZoneId.of(zone)))) {
            throw ApiException.badRequest("The credit date cannot be in the future");
        }
        p.setPaidOn(paidOn);
        p.setPaymentMode(mode);
        p.setPaymentRef(reference == null || reference.isBlank() ? null : reference.strip());
        p.setPaymentRemarks(remarks == null || remarks.isBlank() ? null : remarks.strip());
        p.setPaidRecordedBy(employeeRepository.getReferenceById(me.id()));
    }

    @Transactional
    public PayslipView unpublish(AuthUser me, Long id) {
        Payslip p = full(id);
        Employee e = access.visible(me, p.getEmployee().getId());
        access.requireEditable(me, e);
        if (p.getPaidOn() != null) {
            throw ApiException.conflict("The salary is recorded as credited; undo the credit first");
        }
        p.setStatus(PayslipStatus.DRAFT);
        p.setPublishedAt(null);
        p.setPublishedBy(null);
        p.setUpdatedBy(employeeRepository.getReferenceById(me.id()));
        return view(payslipRepository.saveAndFlush(p), true, true);
    }

    @Transactional
    public void delete(AuthUser me, Long id) {
        Payslip p = full(id);
        Employee e = access.visible(me, p.getEmployee().getId());
        access.requireEditable(me, e);
        if (p.getStatus() == PayslipStatus.PUBLISHED) {
            throw ApiException.conflict("A published payslip cannot be deleted; move it back to draft first");
        }
        payslipRepository.delete(p);
    }

    // ---- team pay: the people below me, as they see their own pay ----

    /** One person Team pay can show. */
    public record TeamPayPerson(Long id, String name, String empNumber, String designationName, Long brandId, String brandCode,
                                String brandName) {
    }

    /**
     * Everyone whose pay I may watch: the people reporting to me, directly or further down; for owners and admins
     * everyone in their scope (TeamAccess). Not me (that is My pay).
     */
    @Transactional(readOnly = true)
    public List<TeamPayPerson> teamPeople(AuthUser me) {
        return teamAccess.team(me).stream()
                .sorted(Comparator.comparing(Employee::getName, String.CASE_INSENSITIVE_ORDER))
                .map(e -> new TeamPayPerson(e.getId(), e.getName(), e.getEmpNumber(),
                        e.getDesignation() == null ? null : e.getDesignation().getName(),
                        e.getBrand() == null ? null : e.getBrand().getId(), e.getBrand() == null ? null : e.getBrand().getCode(),
                        e.getBrand() == null ? null : e.getBrand().getName()))
                .toList();
    }

    /** Their pay for the year, exactly as My pay shows it to them (published payslips only). */
    @Transactional(readOnly = true)
    public MyPay teamPay(AuthUser me, Long employeeId, Integer fy) {
        return myPay(asThem(me, employeeId), fy);
    }

    @Transactional(readOnly = true)
    public PayslipView teamPayslip(AuthUser me, Long employeeId, Long id) {
        return myPayslip(asThem(me, employeeId), id);
    }

    /** The person, read as themselves, when they are below me (or in my scope). */
    private AuthUser asThem(AuthUser me, Long employeeId) {
        Employee e = employeeRepository.findWithBrandById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));
        if (e.getId().equals(me.id())) {
            throw ApiException.badRequest("Your own pay is under My pay");
        }
        if (!teamAccess.canView(me, e)) {
            throw ApiException.forbidden("You can only see the pay of the people reporting to you");
        }
        return AuthUser.of(e);
    }

    // ---- the employee: own pay ----

    @Transactional(readOnly = true)
    public MyPay myPay(AuthUser me, Integer fyOrNull) {
        int fy = fyOrNull == null ? currentFy() : fyOrNull;
        SalaryView salary = payrollService.salary(me, me.id(), fy);
        List<Payslip> mine = payslipRepository.ofEmployeeYear(me.id(), fy).stream().filter(PayslipService::seenByEmployee).toList();

        BigDecimal earned = ZERO;
        BigDecimal received = ZERO;
        BigDecimal taxes = ZERO;
        Map<YearMonth, Payslip> byMonth = new HashMap<>();
        List<Summary> summaries = new ArrayList<>();
        for (Payslip p : mine) {
            Money m = money(p.getLines());
            earned = earned.add(m.gross());
            received = received.add(m.net());
            taxes = taxes.add(tax(p.getLines()));
            YearMonth ym = YearMonth.of(p.getPeriodYear(), p.getPeriodMonth());
            byMonth.put(ym, p);
            summaries.add(new Summary(p.getId(), p.getPeriodYear(), p.getPeriodMonth(), label(ym), p.getPayDate(),
                    p.getPaidDays(), p.getLopDays(), m, p.getPaidOn(), p.getPaymentMode()));
        }
        Collections.reverse(summaries); // newest first

        List<MonthPay> months = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            YearMonth ym = YearMonth.of(fy, 4).plusMonths(i);
            Payslip p = byMonth.get(ym);
            Money m = p == null ? null : money(p.getLines());
            months.add(new MonthPay(ym.getYear(), ym.getMonthValue(), ym.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                    p == null ? null : p.getId(), m == null ? null : m.gross(), m == null ? null : m.net(),
                    p == null ? null : tax(p.getLines())));
        }

        // what the year pays: payslips as made, the months to come worked out (PayEngine)
        BigDecimal expectedGross = salary.set() ? salary.totals().yearGross() : ZERO;
        BigDecimal expectedNet = salary.set() ? salary.totals().yearNet() : ZERO;
        BigDecimal expectedTax = ZERO;
        if (salary.set()) {
            for (PlanMonthView pm : salary.plan()) {
                expectedTax = expectedTax.add(pm.incomeTax()).add(pm.professionalTax());
            }
        }
        BigDecimal balance = expectedGross.subtract(earned).max(ZERO);
        Dashboard d = new Dashboard(expectedGross, expectedNet, expectedTax, earned, received, taxes, balance, mine.size(),
                salary.set() ? salary.totals().monthsInYear() : 0);

        TreeSet<Integer> years = new TreeSet<>(Comparator.reverseOrder());
        years.addAll(payslipRepository.publishedYears(me.id()));
        years.add(currentFy());
        years.add(fy);
        return new MyPay(fy, PayrollService.label(fy), List.copyOf(years), salary, d, months, summaries);
    }

    /** The person's own payslip, once published and credited. */
    @Transactional(readOnly = true)
    public PayslipView myPayslip(AuthUser me, Long id) {
        Payslip p = full(id);
        if (!p.getEmployee().getId().equals(me.id()) || !seenByEmployee(p)) {
            throw new ResourceNotFoundException("Payslip not found: " + id);
        }
        return view(p, false, false);
    }

    // ---- working it out ----

    private PayEngine.Context context(Employee e, int fy) {
        return engine.context(e, fy, salaryRepository.revisions(e.getId(), fy), taxYearRepository.find(e.getId(), fy).orElse(null),
                payslipRepository.ofEmployeeYear(e.getId(), fy));
    }

    /** This payslip's month with these days and typed amounts (the payslip itself left out of the year's payslips). */
    private PayEngine.MonthResult work(Payslip p, Employee e, int working, BigDecimal lop, Map<Long, BigDecimal> typed) {
        if (lop.remainder(HALF).signum() != 0) {
            throw ApiException.badRequest("LOP days are whole or half days, e.g. 1.5");
        }
        YearMonth ym = YearMonth.of(p.getPeriodYear(), p.getPeriodMonth());
        PayEngine.MonthResult r = engine.month(context(e, p.getFyStartYear()), ym, working, lop, typed, p.getId());
        if (lop.compareTo(BigDecimal.valueOf(r.pay().employedDays())) > 0) {
            throw ApiException.badRequest("Unpaid (LOP) days cannot be more than the " + r.pay().employedDays()
                    + " days employed this month");
        }
        return r;
    }

    /** The amounts typed, by component: a line given by its id, or by its component's code. */
    private static Map<Long, BigDecimal> typed(Payslip p, List<LineAmount> amounts) {
        if (amounts == null) {
            return Map.of();
        }
        Map<Long, PayslipLine> byId = p.getLines().stream().collect(Collectors.toMap(PayslipLine::getId, Function.identity()));
        Map<String, Long> byCode = p.getLines().stream().filter(l -> l.getComponent() != null)
                .collect(Collectors.toMap(l -> l.getCode().toUpperCase(Locale.ROOT), l -> l.getComponent().getId(), (a, b) -> a));
        Map<Long, BigDecimal> out = new HashMap<>();
        for (LineAmount la : amounts) {
            Long componentId;
            if (la.getId() != null) {
                PayslipLine l = byId.get(la.getId());
                if (l == null) {
                    throw ApiException.badRequest("Line " + la.getId() + " is not on this payslip");
                }
                componentId = l.getComponent() == null ? null : l.getComponent().getId();
            } else {
                componentId = la.getCode() == null ? null : byCode.get(la.getCode().toUpperCase(Locale.ROOT));
            }
            if (componentId != null) {
                out.put(componentId, la.getAmount());
            }
        }
        return out;
    }

    /** The payslip's days and lines, as worked out; a line that stays keeps its id. */
    private static void fill(Payslip p, PayEngine.MonthPay pay) {
        YearMonth ym = YearMonth.of(p.getPeriodYear(), p.getPeriodMonth());
        p.setDaysInMonth(ym.lengthOfMonth());
        p.setWorkingDays(pay.workingDays());
        p.setEmployedDays(pay.employedDays());
        p.setLopDays(pay.lopDays());
        p.setPaidDays(pay.paidDays());
        Map<Long, PayslipLine> kept = new HashMap<>();
        for (PayslipLine l : p.getLines()) {
            if (l.getComponent() != null) {
                kept.putIfAbsent(l.getComponent().getId(), l);
            }
        }
        Set<Long> worked = pay.lines().stream().map(l -> l.component().getId()).collect(Collectors.toSet());
        p.getLines().removeIf(l -> l.getComponent() == null || !worked.contains(l.getComponent().getId()) || kept.get(l.getComponent().getId()) != l);
        int order = 0;
        for (PayEngine.Line line : pay.lines()) {
            PayComponent c = line.component();
            PayslipLine l = kept.get(c.getId());
            if (l == null) {
                l = new PayslipLine();
                l.setPayslip(p);
                p.getLines().add(l);
            }
            l.setComponent(c);
            l.setCode(c.getCode());
            l.setName(c.getName());
            l.setKind(c.getKind());
            l.setTax(c.isTax());
            l.setSortOrder(order += 10);
            l.setFullAmount(line.fullAmount());
            l.setAmount(line.amount());
        }
    }

    /** Paid for its days: still employed, or left (exit date) and owed the last month. */
    private static boolean payable(Employee e) {
        return e.isActive() || e.getExitDate() != null;
    }

    /** The employee sees a payslip once it is published and the salary is credited. */
    private static boolean seenByEmployee(Payslip p) {
        return p.getStatus() == PayslipStatus.PUBLISHED && p.getPaidOn() != null;
    }

    private void publishOne(AuthUser me, Payslip p) {
        p.setStatus(PayslipStatus.PUBLISHED);
        p.setPublishedAt(Instant.now());
        p.setPublishedBy(employeeRepository.getReferenceById(me.id()));
    }

    /** The person's published payslips of the year before this one's month, by component code (for the employee: credited ones). */
    private Map<String, BigDecimal> ytdBefore(Payslip p, boolean forPayroll) {
        int key = p.getPeriodYear() * 12 + p.getPeriodMonth();
        Map<String, BigDecimal> ytd = new HashMap<>();
        for (Payslip other : payslipRepository.ofEmployeeYear(p.getEmployee().getId(), p.getFyStartYear())) {
            if (other.getId().equals(p.getId()) || other.getStatus() != PayslipStatus.PUBLISHED
                    || (!forPayroll && other.getPaidOn() == null)
                    || other.getPeriodYear() * 12 + other.getPeriodMonth() > key) {
                continue;
            }
            other.getLines().forEach(l -> ytd.merge(l.getCode(), l.getAmount(), BigDecimal::add));
        }
        return ytd;
    }

    /**
     * The payslip with year-to-date amounts per line: this payslip plus the person's published payslips of the year
     * up to this month (for a draft, payroll sees its year to date as if it were published). For the employee, only
     * what they can see counts (published and credited).
     */
    private PayslipView view(Payslip p, boolean editable, boolean forPayroll) {
        Map<String, BigDecimal> ytd = ytdBefore(p, forPayroll);
        PayslipTax tax = null;
        Set<String> auto = Set.of();
        if (forPayroll && p.getStatus() == PayslipStatus.DRAFT) {
            Map<Long, BigDecimal> typed = p.getLines().stream().filter(l -> l.getComponent() != null)
                    .collect(Collectors.toMap(l -> l.getComponent().getId(), PayslipLine::getAmount, (a, b) -> a));
            PayEngine.MonthResult r = engine.month(context(p.getEmployee(), p.getFyStartYear()), YearMonth.of(p.getPeriodYear(), p.getPeriodMonth()),
                    p.getWorkingDays(), p.getLopDays(), typed, p.getId());
            tax = tax(r.tax(), r.pay());
            auto = r.pay().lines().stream().filter(PayEngine.Line::auto).map(l -> l.component().getCode()).collect(Collectors.toSet());
        }
        final Set<String> worked = auto;
        List<LineView> lines = p.getLines().stream()
                .map(l -> new LineView(l.getId(), l.getCode(), l.getName(), l.getKind(), l.isTax(), l.getFullAmount(),
                        l.getAmount(), ytd.getOrDefault(l.getCode(), ZERO).add(l.getAmount()), worked.contains(l.getCode())))
                .toList();
        Money m = money(p.getLines());
        YearMonth ym = YearMonth.of(p.getPeriodYear(), p.getPeriodMonth());
        return new PayslipView(p.getId(), Person.of(p.getEmployee()),
                p.getDesignationName() != null ? p.getDesignationName()
                        : p.getEmployee().getDesignation() == null ? null : p.getEmployee().getDesignation().getName(),
                p.getPeriodYear(), p.getPeriodMonth(), label(ym), p.getFyStartYear(), p.getStatus(), p.getPayDate(),
                p.getDaysInMonth(), p.getWorkingDays(), p.getEmployedDays(), p.getLopDays(), p.getPaidDays(),
                forPayroll ? p.getUnpaidLeaveDays() : null, lines, m,
                RupeesInWords.of(m.net()),
                company(p.getEmployee().getBrand()), forPayroll && editable,
                p.getPublishedBy() == null ? null : p.getPublishedBy().getName(), p.getPublishedAt(), p.getUpdatedAt(),
                p.getPaidOn(), p.getPaymentMode(), p.getPaymentRef(), forPayroll ? p.getPaymentRemarks() : null,
                p.getPaidRecordedBy() == null ? null : p.getPaidRecordedBy().getName(), tax);
    }

    /** The month's income tax as payroll does monthly TDS; null for a Manual tax year. */
    private static PayslipTax tax(PayEngine.TaxWork t, PayEngine.MonthPay pay) {
        if (t == null || t.calc() == null) {
            return null;
        }
        return new PayslipTax(t.regime(), t.calc().rules(), t.calc().income(), t.calc().annualTax(), t.taxBefore(),
                Math.max(0, t.openMonths() - 1), pay.amountOf(PayEngine.TDS), t.calc().rulesFromEarlierYear());
    }

    /** The person's brand: its name, address, GST number and logo; the company settings fill in what is missing. */
    private Company company(Brand b) {
        if (b == null) {
            return new Company(companyName, blank(companyAddress), null, null, null, null, null, "INR", "₹");
        }
        return new Company(b.getName(), b.getAddress() != null ? b.getAddress() : blank(companyAddress), b.getGstNumber(),
                b.getId(), b.getLogoUpdatedAt(), b.getContactNumber(), b.getEmail(), b.getCurrency().name(), b.getCurrency().symbol());
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static Money money(PayEngine.MonthPay pay) {
        BigDecimal gross = pay.gross();
        BigDecimal deductions = pay.total(PayComponentKind.DEDUCTION);
        BigDecimal reimbursements = pay.total(PayComponentKind.REIMBURSEMENT);
        return new Money(gross, deductions, reimbursements, gross.subtract(deductions).add(reimbursements));
    }

    private static Money money(Collection<PayslipLine> lines) {
        BigDecimal gross = ZERO;
        BigDecimal deductions = ZERO;
        BigDecimal reimbursements = ZERO;
        for (PayslipLine l : lines) {
            switch (l.getKind()) {
                case EARNING -> gross = gross.add(l.getAmount());
                case DEDUCTION -> deductions = deductions.add(l.getAmount());
                case REIMBURSEMENT -> reimbursements = reimbursements.add(l.getAmount());
            }
        }
        return new Money(gross, deductions, reimbursements, gross.subtract(deductions).add(reimbursements));
    }

    private static Money sum(List<Money> all) {
        BigDecimal g = ZERO, d = ZERO, r = ZERO, n = ZERO;
        for (Money m : all) {
            g = g.add(m.gross());
            d = d.add(m.deductions());
            r = r.add(m.reimbursements());
            n = n.add(m.net());
        }
        return new Money(g, d, r, n);
    }

    private static BigDecimal tax(Collection<PayslipLine> lines) {
        return lines.stream().filter(l -> l.isTax() && l.getKind() == PayComponentKind.DEDUCTION)
                .map(PayslipLine::getAmount).reduce(ZERO, BigDecimal::add);
    }

    private Payslip full(Long id) {
        return payslipRepository.findFull(id).orElseThrow(() -> new ResourceNotFoundException("Payslip not found: " + id));
    }

    private static YearMonth yearMonth(int year, int month) {
        if (year < 2000 || year > 2100 || month < 1 || month > 12) {
            throw ApiException.badRequest("Unknown month " + year + "-" + month);
        }
        return YearMonth.of(year, month);
    }

    /** April 2025 to March 2026 -> 2025. */
    static int fyOf(YearMonth ym) {
        return ym.getMonthValue() >= 4 ? ym.getYear() : ym.getYear() - 1;
    }

    /** "September 2025". */
    static String label(YearMonth ym) {
        return ym.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + ym.getYear();
    }

    private int currentFy() {
        return fyOf(YearMonth.now(ZoneId.of(zone)));
    }
}
