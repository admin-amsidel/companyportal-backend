-- Copyright (c) 2026 IANEL Technologies. All rights reserved.
--
-- UNDO of V4__leave_revoke.sql: revoked leave becomes cancelled (its days stay given back), the revoke details are lost,
-- and V4 leaves Flyway's history. Never run at start-up: run with FlywayRevertTool (backend README, "Reverting the last
-- migration"), after deploying a backend without the V4 change.
-- By hand in the Supabase SQL editor instead: run "set search_path to portal;" first.

update leave_requests set status = 'CANCELLED' where status = 'REVOKED';
alter table leave_requests drop constraint if exists fk_leave_request_revoked_by;
alter table leave_requests drop column if exists revoke_note;
alter table leave_requests drop column if exists revoked_at;
alter table leave_requests drop column if exists revoked_by;
alter table leave_requests drop constraint if exists leave_requests_status_check;
alter table leave_requests add constraint leave_requests_status_check
    check (status in ('PENDING','APPROVED','REJECTED','CANCELLED'));

delete from flyway_schema_history where version = '4';
