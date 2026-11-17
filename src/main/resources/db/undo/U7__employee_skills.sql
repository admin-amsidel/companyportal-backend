-- Copyright (c) 2026 IANEL Technologies. All rights reserved.
--
-- UNDO of V7__employee_skills.sql: the skills people listed on their profiles are lost, and V7 leaves Flyway's
-- history. Never run at start-up: run with FlywayRevertTool (backend README, "Reverting the last migration"), after
-- deploying a backend without the V7 change.
-- By hand in the Supabase SQL editor instead: run "set search_path to portal;" first.

drop table if exists employee_skills;

delete from flyway_schema_history where version = '7';
