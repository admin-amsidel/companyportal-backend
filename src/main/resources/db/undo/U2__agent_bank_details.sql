-- Copyright (c) 2026 IANEL Technologies. All rights reserved.
--
-- UNDO of V2__agent_bank_details.sql: takes the agents' bank account and IFSC columns off again, and V2 out of Flyway's
-- history, so the database is as it was before V2 (and V2 would run again on the next start).
--
-- NEVER RUN AT START-UP: db/undo is not one of Flyway's locations (spring.flyway.locations=classpath:db/migration), so
-- the backend never sees this file. It is run by hand, on purpose, with FlywayRevertTool (backend README, "Reverting
-- the last migration"), which runs it in the portal schema in one transaction, only when V2 is the latest migration.
--
-- Before reverting, deploy a backend version WITHOUT the V2 change (the Agent entity's bank fields): the start-up check
-- refuses a database that lacks columns the entities have. The bank details stored in these columns are lost.
--
-- By hand in the Supabase SQL editor instead: run "set search_path to portal;" first, then this script.

alter table agents drop column if exists ifsc_code;
alter table agents drop column if exists bank_account_number;

delete from flyway_schema_history where version = '2';
