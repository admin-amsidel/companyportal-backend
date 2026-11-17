-- Copyright (c) 2026 IANEL Technologies. All rights reserved.
--
-- UNDO of V8__employee_experience.sql: the work experience people listed on their profiles is lost, and V8 leaves
-- Flyway's history. Never run at start-up: run with FlywayRevertTool (backend README, "Reverting the last migration"),
-- after deploying a backend without the V8 change.
-- By hand in the Supabase SQL editor instead: run "set search_path to portal;" first.

drop table if exists employee_experiences;

delete from flyway_schema_history where version = '8';
