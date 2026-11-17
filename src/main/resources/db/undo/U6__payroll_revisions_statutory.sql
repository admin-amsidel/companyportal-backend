-- Copyright (c) 2026 IANEL Technologies. All rights reserved.
--
-- UNDO of V6__payroll_revisions_statutory.sql. What is lost: exit dates, the income and tax entered as paid outside
-- the portal, the professional tax slabs, which salaries have EPF / ESI / professional tax worked out, half days of loss
-- of pay (rounded up) and every salary revision but the latest of each person's year (one salary a year again).
-- Never run at start-up: run with FlywayRevertTool (backend README, "Reverting the last migration"), after deploying a
-- backend without the V6 change.
-- By hand in the Supabase SQL editor instead: run "set search_path to portal;" first.

drop table if exists professional_tax_slabs;

alter table payslips drop column if exists unpaid_leave_days;
alter table payslips alter column paid_days type integer using floor(paid_days);
alter table payslips alter column lop_days type integer using ceil(lop_days);

alter table salary_structures add column tax_regime varchar(10) check ((tax_regime in ('NEW','OLD','MANUAL')));
alter table salary_structures add column tax_declared numeric(14,2);
update salary_structures s set tax_regime = t.tax_regime, tax_declared = t.tax_declared
    from payroll_tax_years t where t.employee_id = s.employee_id and t.fy_start_year = s.fy_start_year;
drop table if exists payroll_tax_years;

-- one salary a year again: the latest revision stays (its older revisions' lines go first)
delete from salary_lines l using salary_structures s where l.structure_id = s.id and exists (select 1 from salary_structures n
    where n.employee_id = s.employee_id and n.fy_start_year = s.fy_start_year and n.effective_from > s.effective_from);
delete from salary_structures s where exists (select 1 from salary_structures n
    where n.employee_id = s.employee_id and n.fy_start_year = s.fy_start_year and n.effective_from > s.effective_from);
alter table salary_structures drop column if exists pt_applies;
alter table salary_structures drop column if exists esi_applies;
alter table salary_structures drop column if exists pf_full_wage;
alter table salary_structures drop column if exists pf_applies;
alter table salary_structures drop constraint if exists uk_salary_employee_from;
alter table salary_structures add constraint uk_salary_employee_year unique (employee_id, fy_start_year);

alter table employees drop column if exists exit_date;

delete from flyway_schema_history where version = '6';
