/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.service;

import com.companyportal.backend.dto.PayrollRequests;
import com.companyportal.backend.dto.PayrollViews.*;
import com.companyportal.backend.entity.*;
import com.companyportal.backend.exception.ApiException;
import com.companyportal.backend.exception.ResourceNotFoundException;
import com.companyportal.backend.repository.*;
import com.companyportal.backend.security.AuthUser;
import com.companyportal.backend.security.Menus;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Yearly payroll set-up: the pay components, the professional tax slabs, and each employee's salary for a financial
 * year (April to March), in revisions from their own dates (a raise in October), with the year's income tax set-up.
 * The figures (monthly, the year month by month, the tax) come from PayEngine, as payslips do.
 * Who sees whom: owners and people without a brand see everyone; anyone else with the Payroll menu sees the people of
 * their own brand. Nobody changes their own salary, except an owner.
 */
@Service
@RequiredArgsConstructor
public class PayrollService {

    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    /** Working days in a month unless a salary says otherwise. */
    public static final int DEFAULT_WORKING_DAYS = 30;

    private final PayComponentRepository componentRepository;
    private final SalaryStructureRepository salaryRepository;
    private final PayrollTaxYearRepository taxYearRepository;
    private final PayslipRepository payslipRepository;
    private final ProfessionalTaxSlabRepository slabRepository;
    private final EmployeeRepository employeeRepository;
    private final PayrollAccess access;
    private final MenuAccessService menuAccess;
    private final PayEngine engine;

    @Value("${app.leave.zone:Asia/Kolkata}")
    private String zone;

    // ---- pay components ----

    @Transactional(readOnly = true)
    public List<ComponentView> components() {
        return componentRepository.ordered().stream()
                .map(c -> ComponentView.of(c, componentRepository.inUse(c.getId()))).toList();
    }

    @Transactional
    public ComponentView saveComponent(Long id, PayrollRequests.Component body) {
        String code = body.getCode().strip().toUpperCase(Locale.ROOT);
        boolean taken = id == null ? componentRepository.existsByCodeIgnoreCase(code)
                : componentRepository.existsByCodeIgnoreCaseAndIdNot(code, id);
        if (taken) {
            throw ApiException.conflict("There is already a component with the code " + code);
        }
        PayComponent c = id == null ? new PayComponent() : component(id);
        if (id != null && c.getKind() != body.getKind() && componentRepository.inUse(id)) {
            throw ApiException.conflict(c.getName() + " is used in salaries, so it stays " + label(c.getKind())
                    + "; add a new component instead");
        }
        c.setCode(code);
        c.setName(body.getName().strip());
        c.setKind(body.getKind());
        c.setSortOrder(body.getSortOrder());
        c.setActive(body.isActive());
        c.setTax(body.isTax());
        c = componentRepository.save(c);
        return ComponentView.of(c, id != null && componentRepository.inUse(c.getId()));
    }

    /** Only a component no salary uses; otherwise make it inactive. */
    @Transactional
    public void deleteComponent(Long id) {
        PayComponent c = component(id);
        if (componentRepository.inUse(id)) {
            throw ApiException.conflict(c.getName() + " is used in salaries; make it inactive instead");
        }
        componentRepository.delete(c);
    }

    // ---- professional tax slabs ----

    @Transactional(readOnly = true)
    public List<PtSlabView> ptSlabs() {
        return slabRepository.ordered().stream().map(s -> new PtSlabView(s.getFromIncome(), s.getHalfYearTax())).toList();
    }

    /** Replaces the slabs: the first starts at 0, each one higher than the one before. */
    @Transactional
    public List<PtSlabView> savePtSlabs(PayrollRequests.PtSlabs body) {
        List<PayrollRequests.PtSlab> slabs = body.getSlabs().stream().sorted(Comparator.comparing(PayrollRequests.PtSlab::getFromIncome)).toList();
        if (slabs.getFirst().getFromIncome().signum() != 0) {
            throw ApiException.badRequest("The first slab starts at 0 (usually with no tax)");
        }
        for (int i = 1; i < slabs.size(); i++) {
            if (slabs.get(i).getFromIncome().compareTo(slabs.get(i - 1).getFromIncome()) == 0) {
                throw ApiException.badRequest("Two slabs start at " + slabs.get(i).getFromIncome().stripTrailingZeros().toPlainString());
            }
        }
        slabRepository.deleteAllInBatch();
        slabRepository.flush();
        for (PayrollRequests.PtSlab s : slabs) {
            ProfessionalTaxSlab n = new ProfessionalTaxSlab();
            n.setFromIncome(s.getFromIncome().setScale(2, RoundingMode.HALF_UP));
            n.setHalfYearTax(s.getHalfYearTax().setScale(2, RoundingMode.HALF_UP));
            slabRepository.save(n);
        }
        return ptSlabs();
    }

    // ---- salaries ----

    /** Everyone in scope for a year (active people, and anyone who had a salary that year), with their totals. */
    @Transactional(readOnly = true)
    public YearView year(AuthUser me, Integer fyOrNull) {
        int fy = fyOrNull == null ? currentFy() : fy(fyOrNull);
        Map<Long, List<SalaryStructure>> revisions = salaryRepository.ofYear(fy).stream()
                .collect(Collectors.groupingBy(s -> s.getEmployee().getId()));
        Map<Long, PayrollTaxYear> taxYears = taxYearRepository.ofYear(fy).stream()
                .collect(Collectors.toMap(t -> t.getEmployee().getId(), Function.identity(), (a, b) -> a));
        Map<Long, List<Payslip>> payslips = payslipRepository.ofYear(fy).stream()
                .collect(Collectors.groupingBy(p -> p.getEmployee().getId()));
        List<SalaryRow> rows = new ArrayList<>();
        List<Totals> set = new ArrayList<>();
        for (Employee e : people(me)) {
            List<SalaryStructure> revs = revisions.get(e.getId());
            if (revs == null && !e.isActive()) {
                continue;
            }
            if (revs == null) {
                rows.add(new SalaryRow(Person.of(e), false, null, DEFAULT_WORKING_DAYS, empty(), editable(me, e), null, null, null, 0));
                continue;
            }
            PayEngine.Context ctx = engine.context(e, fy, revs, taxYears.get(e.getId()), payslips.getOrDefault(e.getId(), List.of()));
            PayEngine.YearPlan plan = engine.plan(ctx);
            SalaryStructure latest = ctx.revisions().getLast();
            Totals t = totals(ctx, latest, plan);
            set.add(t);
            rows.add(new SalaryRow(Person.of(e), true, latest.getEffectiveFrom(), latest.getWorkingDays(), t, editable(me, e),
                    latest.getUpdatedBy() == null ? null : latest.getUpdatedBy().getName(), latest.getUpdatedAt(), ctx.regime(), revs.size()));
        }
        return new YearView(fy, label(fy), years(), rows, sum(set), set.size());
    }

    /** One revision of the year (revisionId), or the latest when none is asked for. */
    @Transactional(readOnly = true)
    public SalaryView salary(AuthUser me, Long employeeId, int fyStart, Long revisionId) {
        int fy = fy(fyStart);
        Employee e = visible(me, employeeId);
        List<SalaryStructure> revs = salaryRepository.revisions(e.getId(), fy);
        SalaryStructure s = revisionId != null ? revision(revs, revisionId) : revs.isEmpty() ? null : revs.getLast();
        return view(me, e, fy, revs, s);
    }

    @Transactional(readOnly = true)
    public SalaryView salary(AuthUser me, Long employeeId, int fyStart) {
        return salary(me, employeeId, fyStart, null);
    }

    @Transactional
    public SalaryView save(AuthUser me, Long employeeId, int fyStart, PayrollRequests.Salary body) {
        int fy = fy(fyStart);
        Employee e = visible(me, employeeId);
        access.requireEditable(me, e);
        LocalDate from = body.getEffectiveFrom();
        checkStart(e, fy, from);
        List<SalaryStructure> revs = salaryRepository.revisions(e.getId(), fy);
        SalaryStructure s;
        SalaryStructure sameDay = revs.stream().filter(r -> r.getEffectiveFrom().equals(from)).findFirst().orElse(null);
        if (body.getRevisionId() != null) {
            s = revision(revs, body.getRevisionId());
        } else if (sameDay != null) {
            // no revision named: the one from that day is changed
            s = sameDay;
        } else {
            s = new SalaryStructure();
            s.setEmployee(e);
            s.setFyStartYear(fy);
            // a raise takes the deductions of the salary before it
            SalaryStructure before = PayEngine.inForce(revs, from);
            if (before != null) {
                s.setPfApplies(before.isPfApplies());
                s.setPfFullWage(before.isPfFullWage());
                s.setEsiApplies(before.isEsiApplies());
                s.setPtApplies(before.isPtApplies());
            }
        }
        final SalaryStructure editing = s;
        revs.stream().filter(r -> r != editing && r.getEffectiveFrom().equals(from)).findAny().ifPresent(r -> {
            throw ApiException.conflict("There is already a salary revision from " + from + "; change that one instead");
        });

        Map<Long, PayComponent> components = componentRepository.findAll().stream()
                .collect(Collectors.toMap(PayComponent::getId, Function.identity()));
        Map<Long, BigDecimal> amounts = new LinkedHashMap<>();
        for (PayrollRequests.Line line : body.getLines()) {
            if (!components.containsKey(line.getComponentId())) {
                throw ApiException.badRequest("Unknown pay component " + line.getComponentId());
            }
            amounts.merge(line.getComponentId(), line.getMonthly().setScale(2, RoundingMode.HALF_UP), BigDecimal::add);
        }
        if (amounts.entrySet().stream().noneMatch(a -> a.getValue().signum() > 0
                && components.get(a.getKey()).getKind() == PayComponentKind.EARNING)) {
            throw ApiException.badRequest("Enter at least one earning");
        }
        if (body.getPfApplies() != null) {
            s.setPfApplies(body.getPfApplies());
        }
        if (body.getPfFullWage() != null) {
            s.setPfFullWage(body.getPfFullWage());
        }
        if (body.getEsiApplies() != null) {
            s.setEsiApplies(body.getEsiApplies());
        }
        if (body.getPtApplies() != null) {
            s.setPtApplies(body.getPtApplies());
        }

        // income tax: worked out under the new or old regime, or typed in (Manual, the default) by someone allowed to;
        // only someone with Tax regime selection changes the regime (no regime sent: the year's, Manual for a new one)
        PayrollTaxYear ty = taxYearRepository.find(e.getId(), fy).orElseGet(() -> {
            PayrollTaxYear n = new PayrollTaxYear();
            n.setEmployee(e);
            n.setFyStartYear(fy);
            return n;
        });
        TaxRegime current = ty.getId() != null ? ty.getTaxRegime() : TaxRegime.MANUAL;
        TaxRegime regime = body.getTaxRegime() != null ? body.getTaxRegime() : current;
        if (regime != current && !menuAccess.can(me, Menus.PAY_TAX_REGIME)) {
            throw ApiException.forbidden("Choosing the tax regime needs the Tax regime selection access; this salary stays "
                    + current.name().charAt(0) + current.name().substring(1).toLowerCase(Locale.ROOT));
        }
        PayComponent tds = byCode(components.values(), PayEngine.TDS);
        if (tds != null) {
            if (regime == TaxRegime.MANUAL) {
                BigDecimal before = s.getLines().stream().filter(l -> l.getComponent().getId().equals(tds.getId())).map(SalaryLine::getMonthlyAmount)
                        .findFirst().orElse(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
                BigDecimal asked = amounts.getOrDefault(tds.getId(), BigDecimal.ZERO);
                if (asked.compareTo(before) != 0 && !menuAccess.can(me, Menus.PAY_TAX_MANUAL)) {
                    throw ApiException.forbidden("Typing the income tax by hand needs the Manual income tax access; choose the new or old regime");
                }
            } else {
                amounts.remove(tds.getId());
            }
        }
        // what payroll works out is not kept on the salary
        dropWorkedOut(amounts, components.values(), PayEngine.EPF, s.isPfApplies());
        dropWorkedOut(amounts, components.values(), PayEngine.ESI, s.isEsiApplies());
        dropWorkedOut(amounts, components.values(), PayEngine.PT, s.isPtApplies());

        ty.setTaxRegime(regime);
        if (regime == TaxRegime.OLD && body.getTaxDeclared() != null) {
            ty.setTaxDeclared(body.getTaxDeclared().setScale(2, RoundingMode.HALF_UP));
        }
        if (body.getOutsideIncome() != null) {
            ty.setOutsideIncome(body.getOutsideIncome().signum() == 0 ? null : body.getOutsideIncome().setScale(2, RoundingMode.HALF_UP));
        }
        if (body.getOutsideTax() != null) {
            ty.setOutsideTax(body.getOutsideTax().signum() == 0 ? null : body.getOutsideTax().setScale(2, RoundingMode.HALF_UP));
        }
        Employee by = employeeRepository.getReferenceById(me.id());
        ty.setUpdatedBy(by);

        s.setEffectiveFrom(from);
        s.setWorkingDays(body.getWorkingDays() == null ? DEFAULT_WORKING_DAYS : body.getWorkingDays());
        s.setNotes(body.getNotes() == null || body.getNotes().isBlank() ? null : body.getNotes().strip());
        s.setUpdatedBy(by);
        if (body.getPan() != null) {
            e.setPanNumber(body.getPan().isBlank() ? null : body.getPan().strip().toUpperCase(Locale.ROOT));
        }
        if (body.getBankAccount() != null) {
            e.setBankAccountNumber(body.getBankAccount().isBlank() ? null : body.getBankAccount().strip());
        }
        if (body.getIfsc() != null) {
            e.setIfscCode(body.getIfsc().isBlank() ? null : body.getIfsc().strip().toUpperCase(Locale.ROOT));
        }
        if (body.getUpi() != null) {
            e.setUpiId(body.getUpi().isBlank() ? null : body.getUpi().strip().toLowerCase(Locale.ROOT));
        }
        // keep the lines that stay (same ids), drop the ones now 0, add the new ones
        Map<Long, SalaryLine> existing = s.getLines().stream()
                .collect(Collectors.toMap(l -> l.getComponent().getId(), Function.identity()));
        s.getLines().removeIf(l -> amounts.getOrDefault(l.getComponent().getId(), BigDecimal.ZERO).signum() == 0);
        amounts.forEach((componentId, amount) -> {
            if (amount.signum() == 0) {
                return;
            }
            SalaryLine l = existing.get(componentId);
            if (l == null) {
                l = new SalaryLine();
                l.setStructure(s);
                l.setComponent(components.get(componentId));
                s.getLines().add(l);
            }
            l.setMonthlyAmount(amount);
        });
        SalaryStructure saved = salaryRepository.saveAndFlush(s);
        taxYearRepository.saveAndFlush(ty);
        List<SalaryStructure> now = salaryRepository.revisions(e.getId(), fy);
        return view(me, e, fy, now, revision(now, saved.getId()));
    }

    /** One revision (revisionId), or the whole year's salary with its tax set-up when none is given. */
    @Transactional
    public void delete(AuthUser me, Long employeeId, int fyStart, Long revisionId) {
        int fy = fy(fyStart);
        Employee e = visible(me, employeeId);
        access.requireEditable(me, e);
        List<SalaryStructure> revs = salaryRepository.revisions(e.getId(), fy);
        if (revisionId != null) {
            salaryRepository.delete(revision(revs, revisionId));
            if (revs.size() > 1) {
                return;
            }
        } else {
            salaryRepository.deleteAll(revs);
        }
        taxYearRepository.find(e.getId(), fy).ifPresent(taxYearRepository::delete);
    }

    @Transactional
    public void delete(AuthUser me, Long employeeId, int fyStart) {
        delete(me, employeeId, fyStart, null);
    }

    /** The year's income tax under both regimes, with a revision being typed standing in for its saved one. */
    @Transactional(readOnly = true)
    public Map<String, IncomeTax.Calc> taxPreview(AuthUser me, int fyStart, PayrollRequests.TaxPreview body) {
        int fy = fy(fyStart);
        Employee e = visible(me, body.getEmployeeId());
        Map<Long, PayComponent> components = componentRepository.findAll().stream()
                .collect(Collectors.toMap(PayComponent::getId, Function.identity()));
        SalaryStructure typed = new SalaryStructure();
        typed.setEmployee(e);
        typed.setFyStartYear(fy);
        typed.setEffectiveFrom(body.getEffectiveFrom());
        typed.setWorkingDays(body.getWorkingDays() == null ? DEFAULT_WORKING_DAYS : body.getWorkingDays());
        typed.setPfApplies(body.isPfApplies());
        typed.setPfFullWage(body.isPfFullWage());
        typed.setEsiApplies(body.isEsiApplies());
        typed.setPtApplies(body.isPtApplies());
        for (PayrollRequests.Line l : body.getLines()) {
            PayComponent c = components.get(l.getComponentId());
            if (c == null || l.getMonthly().signum() == 0) {
                continue;
            }
            SalaryLine line = new SalaryLine();
            line.setStructure(typed);
            line.setComponent(c);
            line.setMonthlyAmount(l.getMonthly().setScale(2, RoundingMode.HALF_UP));
            typed.getLines().add(line);
        }
        List<SalaryStructure> revs = new ArrayList<>(salaryRepository.revisions(e.getId(), fy).stream()
                .filter(r -> !r.getId().equals(body.getRevisionId()) && !r.getEffectiveFrom().equals(body.getEffectiveFrom())).toList());
        revs.add(typed);
        PayrollTaxYear ty = taxYearRepository.find(e.getId(), fy).orElse(null);
        PayEngine.Context ctx = engine.context(e, fy, revs, ty, payslipRepository.ofEmployeeYear(e.getId(), fy));
        BigDecimal outsideIncome = body.getOutsideIncome() != null ? body.getOutsideIncome() : ctx.outsideIncome();
        BigDecimal outsideTax = body.getOutsideTax() != null ? body.getOutsideTax() : ctx.outsideTax();
        Map<String, IncomeTax.Calc> out = new LinkedHashMap<>();
        out.put("NEW", engine.plan(ctx.with(ctx.revisions(), TaxRegime.NEW, null, outsideIncome, outsideTax)).tax().calc());
        out.put("OLD", engine.plan(ctx.with(ctx.revisions(), TaxRegime.OLD, body.getDeclared() != null ? body.getDeclared() : ctx.declared(),
                outsideIncome, outsideTax)).tax().calc());
        return out;
    }

    // ---- rules and helpers ----

    /** Inside the year, not before the person joined (PORTAL Q3), not after their exit date. */
    private static void checkStart(Employee e, int fy, LocalDate from) {
        if (from.isBefore(start(fy)) || from.isAfter(end(fy))) {
            throw ApiException.badRequest("Effective from must be inside " + label(fy) + " (" + start(fy) + " to " + end(fy) + ")");
        }
        if (e.getJoinedDate() != null && from.isBefore(e.getJoinedDate())) {
            throw ApiException.badRequest("The salary cannot start before " + e.getName() + " joined, on " + e.getJoinedDate());
        }
        if (e.getExitDate() != null && from.isAfter(e.getExitDate())) {
            throw ApiException.badRequest("The salary cannot start after " + e.getName() + "'s exit date, " + e.getExitDate());
        }
    }

    private static void dropWorkedOut(Map<Long, BigDecimal> amounts, Collection<PayComponent> components, String code, boolean auto) {
        PayComponent c = byCode(components, code);
        if (auto && c != null) {
            amounts.remove(c.getId());
        }
    }

    private static PayComponent byCode(Collection<PayComponent> all, String code) {
        return all.stream().filter(c -> code.equalsIgnoreCase(c.getCode())).findFirst().orElse(null);
    }

    private static SalaryStructure revision(List<SalaryStructure> revs, Long id) {
        return revs.stream().filter(r -> r.getId().equals(id)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Salary revision not found: " + id));
    }

    private List<Employee> people(AuthUser me) {
        return access.people(me);
    }

    private Employee visible(AuthUser me, Long employeeId) {
        return access.visible(me, employeeId);
    }

    private boolean editable(AuthUser me, Employee e) {
        return access.editable(me, e);
    }

    private SalaryView view(AuthUser me, Employee e, int fy, List<SalaryStructure> revs, SalaryStructure s) {
        PayrollTaxYear ty = taxYearRepository.find(e.getId(), fy).orElse(null);
        PayEngine.Context ctx = engine.context(e, fy, revs, ty, payslipRepository.ofEmployeeYear(e.getId(), fy));
        PayEngine.YearPlan plan = engine.plan(ctx);
        Map<Long, BigDecimal> monthly = s == null ? Map.of() : fullMonth(ctx, s, plan);
        Set<Long> auto = s == null ? Set.of() : autoComponents(ctx, s);
        List<LineView> lines = componentRepository.ordered().stream()
                .filter(c -> c.isActive() || monthly.containsKey(c.getId()))
                .map(c -> {
                    BigDecimal m = monthly.getOrDefault(c.getId(), BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
                    return new LineView(c.getId(), c.getCode(), c.getName(), c.getKind(), c.isActive(), c.isTax(), m,
                            m.multiply(TWELVE), auto.contains(c.getId()));
                }).toList();
        // a new salary starts on 1 April, or on the joining date when they joined later in the year
        LocalDate from = s != null ? s.getEffectiveFrom()
                : e.getJoinedDate() != null && e.getJoinedDate().isAfter(start(fy)) && !e.getJoinedDate().isAfter(end(fy))
                ? e.getJoinedDate() : start(fy);
        Totals t = s == null ? empty() : totals(ctx, s, plan);
        List<RevisionView> revisions = new ArrayList<>();
        for (int i = 0; i < ctx.revisions().size(); i++) {
            SalaryStructure r = ctx.revisions().get(i);
            LocalDate until = i + 1 < ctx.revisions().size() ? ctx.revisions().get(i + 1).getEffectiveFrom().minusDays(1) : null;
            revisions.add(new RevisionView(r.getId(), r.getEffectiveFrom(), until, gross(r)));
        }
        List<PlanMonthView> months = plan.months().stream()
                .map(m -> new PlanMonthView(m.month().getYear(), m.month().getMonthValue(),
                        m.month().getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH), m.source().name(), m.gross(), m.deductions(),
                        m.reimbursements(), m.net(), m.incomeTax(), m.professionalTax())).toList();
        return new SalaryView(Person.of(e), fy, label(fy), start(fy), end(fy), s != null, from,
                s == null ? DEFAULT_WORKING_DAYS : s.getWorkingDays(),
                s == null ? null : s.getNotes(), lines, t, editable(me, e),
                s == null || s.getUpdatedBy() == null ? null : s.getUpdatedBy().getName(), s == null ? null : s.getUpdatedAt(),
                taxView(me, ctx, plan), s == null ? null : s.getId(), revisions,
                s != null && s.isPfApplies(), s != null && s.isPfFullWage(), s != null && s.isEsiApplies(), s != null && s.isPtApplies(),
                ty == null ? null : ty.getOutsideIncome(), ty == null ? null : ty.getOutsideTax(), months);
    }

    /** The components payroll works out for this revision. */
    private static Set<Long> autoComponents(PayEngine.Context ctx, SalaryStructure s) {
        Set<Long> out = new HashSet<>();
        for (PayComponent c : ctx.components().values()) {
            if (PayEngine.isAuto(c.getCode().toUpperCase(Locale.ROOT), s, ctx.regime())) {
                out.add(c.getId());
            }
        }
        return out;
    }

    /**
     * A full month at this revision, by component: its own amounts, with EPF and ESI on its full pay, and the
     * professional tax and income tax of a month the year's plan works out while it is in force.
     */
    private static Map<Long, BigDecimal> fullMonth(PayEngine.Context ctx, SalaryStructure s, PayEngine.YearPlan plan) {
        Map<Long, BigDecimal> out = new LinkedHashMap<>();
        for (SalaryLine l : s.getLines()) {
            if (!PayEngine.isAuto(l.getComponent().getCode().toUpperCase(Locale.ROOT), s, ctx.regime())) {
                out.merge(l.getComponent().getId(), l.getMonthlyAmount(), BigDecimal::add);
            }
        }
        BigDecimal gross = gross(s);
        PayComponent epf = ctx.components().get(PayEngine.EPF);
        if (s.isPfApplies() && epf != null) {
            BigDecimal wage = s.getLines().stream().filter(l -> l.getComponent().getKind() == PayComponentKind.EARNING
                    && (PayEngine.BASIC.equalsIgnoreCase(l.getComponent().getCode()) || PayEngine.DA.equalsIgnoreCase(l.getComponent().getCode())))
                    .map(SalaryLine::getMonthlyAmount).reduce(ZERO, BigDecimal::add);
            out.put(epf.getId(), PayEngine.pf(wage, s));
        }
        PayComponent esi = ctx.components().get(PayEngine.ESI);
        if (s.isEsiApplies() && esi != null) {
            out.put(esi.getId(), gross.compareTo(PayEngine.ESI_GROSS_LIMIT) <= 0 ? PayEngine.esi(gross) : ZERO);
        }
        PayEngine.MonthPay sample = plan.open().values().stream().filter(m -> m.revision() == s).findFirst()
                .orElse(plan.open().values().stream().findFirst().orElse(null));
        PayComponent pt = ctx.components().get(PayEngine.PT);
        if (s.isPtApplies() && pt != null) {
            out.put(pt.getId(), sample == null ? ZERO : sample.amountOf(PayEngine.PT));
        }
        PayComponent tds = ctx.components().get(PayEngine.TDS);
        if (ctx.regime() != TaxRegime.MANUAL && tds != null) {
            out.put(tds.getId(), plan.tax() == null ? ZERO : plan.tax().monthTax());
        }
        return out;
    }

    private static Totals totals(PayEngine.Context ctx, SalaryStructure s, PayEngine.YearPlan plan) {
        Map<Long, PayComponent> byId = ctx.components().values().stream().collect(Collectors.toMap(PayComponent::getId, Function.identity()));
        for (SalaryLine l : s.getLines()) {
            byId.putIfAbsent(l.getComponent().getId(), l.getComponent());
        }
        Map<PayComponentKind, BigDecimal> byKind = new EnumMap<>(PayComponentKind.class);
        fullMonth(ctx, s, plan).forEach((id, amount) -> byKind.merge(byId.get(id).getKind(), amount, BigDecimal::add));
        BigDecimal gross = money(byKind.get(PayComponentKind.EARNING));
        BigDecimal deductions = money(byKind.get(PayComponentKind.DEDUCTION));
        BigDecimal reimbursements = money(byKind.get(PayComponentKind.REIMBURSEMENT));
        BigDecimal net = gross.subtract(deductions).add(reimbursements);
        return new Totals(gross, deductions, reimbursements, net, gross.multiply(TWELVE), deductions.multiply(TWELVE),
                reimbursements.multiply(TWELVE), net.multiply(TWELVE), plan.monthsEmployed(), plan.gross(), plan.net());
    }

    private static Totals empty() {
        return new Totals(ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, 0, ZERO, ZERO);
    }

    private static BigDecimal gross(SalaryStructure s) {
        return s.getLines().stream().filter(l -> l.getComponent().getKind() == PayComponentKind.EARNING)
                .map(SalaryLine::getMonthlyAmount).reduce(ZERO, BigDecimal::add);
    }

    /** Totals of the whole payroll (months is not meaningful there: 0). */
    private static Totals sum(List<Totals> all) {
        BigDecimal[] t = new BigDecimal[10];
        Arrays.fill(t, ZERO);
        for (Totals x : all) {
            t[0] = t[0].add(x.monthlyGross());
            t[1] = t[1].add(x.monthlyDeductions());
            t[2] = t[2].add(x.monthlyReimbursements());
            t[3] = t[3].add(x.monthlyNet());
            t[4] = t[4].add(x.annualGross());
            t[5] = t[5].add(x.annualDeductions());
            t[6] = t[6].add(x.annualReimbursements());
            t[7] = t[7].add(x.annualNet());
            t[8] = t[8].add(x.yearGross());
            t[9] = t[9].add(x.yearNet());
        }
        return new Totals(t[0], t[1], t[2], t[3], t[4], t[5], t[6], t[7], 0, t[8], t[9]);
    }

    private static BigDecimal money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }

    /** The year's tax as worked out, both regimes side by side, and what I may do. */
    private TaxView taxView(AuthUser me, PayEngine.Context ctx, PayEngine.YearPlan plan) {
        PayEngine.YearPlan newer = engine.plan(ctx.with(ctx.revisions(), TaxRegime.NEW, null, ctx.outsideIncome(), ctx.outsideTax()));
        PayEngine.YearPlan older = engine.plan(ctx.with(ctx.revisions(), TaxRegime.OLD, ctx.declared(), ctx.outsideIncome(), ctx.outsideTax()));
        PayComponent tds = ctx.components().get(PayEngine.TDS);
        return new TaxView(ctx.regime(), ctx.declared(), plan.tax() == null ? null : plan.tax().calc(), newer.tax().calc(), older.tax().calc(),
                menuAccess.can(me, Menus.PAY_TAX_MANUAL), menuAccess.can(me, Menus.PAY_TAX_REGIME), tds == null ? null : tds.getName(),
                newer.tax().taxBefore());
    }

    private PayComponent component(Long id) {
        return componentRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Pay component not found: " + id));
    }

    private List<Integer> years() {
        TreeSet<Integer> years = new TreeSet<>(Comparator.reverseOrder());
        years.addAll(salaryRepository.years());
        int now = currentFy();
        years.add(now - 1);
        years.add(now);
        years.add(now + 1);
        return List.copyOf(years);
    }

    private int currentFy() {
        LocalDate today = LocalDate.now(ZoneId.of(zone));
        return today.getMonthValue() >= 4 ? today.getYear() : today.getYear() - 1;
    }

    private static int fy(int fyStart) {
        if (fyStart < 2000 || fyStart > 2100) {
            throw ApiException.badRequest("Unknown financial year " + fyStart);
        }
        return fyStart;
    }

    static LocalDate start(int fy) {
        return LocalDate.of(fy, 4, 1);
    }

    static LocalDate end(int fy) {
        return LocalDate.of(fy + 1, 3, 31);
    }

    /** 2025 -> "FY 2025-26". */
    static String label(int fy) {
        return "FY " + fy + "-" + String.format("%02d", (fy + 1) % 100);
    }

    private static String label(PayComponentKind kind) {
        return switch (kind) {
            case EARNING -> "an earning";
            case DEDUCTION -> "a deduction";
            case REIMBURSEMENT -> "a reimbursement";
        };
    }
}
