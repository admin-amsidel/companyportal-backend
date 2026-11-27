-- Copyright (c) 2026 IANEL Technologies. All rights reserved.
--
-- UNDO of V9__window_quotes.sql: every door & window quotation is lost, and V9 leaves Flyway's history. Never run at
-- start-up: run with FlywayRevertTool (backend README, "Reverting the last migration"), after deploying a backend
-- without the V9 change. The Door & window quotes menu stays in the menus table until removed from Menus.java.
-- By hand in the Supabase SQL editor instead: run "set search_path to portal;" first.

drop table if exists window_quotes;

delete from flyway_schema_history where version = '9';
