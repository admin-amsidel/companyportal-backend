-- Copyright (c) 2026 IANEL Technologies. All rights reserved.
-- Leave revoked by an owner or admin: approved leave that has not started can be taken back (the person's plans changed),
-- which gives the days back to their balance. The request keeps its approval and records who revoked it, when and why.

alter table leave_requests drop constraint if exists leave_requests_status_check;
alter table leave_requests add constraint leave_requests_status_check
    check (status in ('PENDING','APPROVED','REJECTED','CANCELLED','REVOKED'));
alter table leave_requests add column revoked_by bigint;
alter table leave_requests add column revoked_at timestamp(6) with time zone;
alter table leave_requests add column revoke_note varchar(300);
alter table leave_requests add constraint fk_leave_request_revoked_by foreign key (revoked_by) references employees;
