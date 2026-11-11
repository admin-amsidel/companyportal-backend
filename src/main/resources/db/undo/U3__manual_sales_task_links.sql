-- Copyright (c) 2026 IANEL Technologies. All rights reserved.
--
-- UNDO of V3__manual_sales_task_links.sql: Manual Sales tasks lose their project and goal links (the customer, project
-- and agent names they show stay), and V3 leaves Flyway's history. Never run at start-up: run with FlywayRevertTool
-- (backend README, "Reverting the last migration"), after deploying a backend without the V3 change.
-- By hand in the Supabase SQL editor instead: run "set search_path to portal;" first.

alter table manual_sales_tasks drop constraint if exists fk_manual_sales_task_goal;
alter table manual_sales_tasks drop constraint if exists fk_manual_sales_task_project;
alter table manual_sales_tasks drop column if exists goal_id;
alter table manual_sales_tasks drop column if exists project_id;

delete from flyway_schema_history where version = '3';
