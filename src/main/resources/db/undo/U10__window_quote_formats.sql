-- Copyright (c) 2026 IANEL Technologies. All rights reserved.
--
-- UNDO of V10__window_quote_formats.sql: the formats, lines, transport, rupee discounts, GST mode and printed texts are
-- lost (quotations saved with lines keep no items, so delete them first), and V10 leaves Flyway's history. Never run at
-- start-up: run with FlywayRevertTool (backend README, "Reverting the last migration"), after deploying a backend
-- without the V10 change. By hand in the Supabase SQL editor instead: run "set search_path to portal;" first.

delete from window_quotes where items_json is null;
alter table window_quotes alter column items_json set not null;
alter table window_quotes drop column lines_json;
alter table window_quotes drop column options_json;

alter table window_quotes add column tax_pct numeric(5,2) not null default 0;
alter table window_quotes add column tax numeric(14,2) not null default 0;
update window_quotes set tax_pct = gst_pct, tax = gst;
alter table window_quotes drop column gst_mode;
alter table window_quotes drop column gst_pct;
alter table window_quotes drop column gst;

alter table window_quotes add column discount_pct numeric(5,2) not null default 0;
update window_quotes set discount_pct = least(discount_value, 50) where discount_type = 'PCT';
alter table window_quotes drop column discount_value;
alter table window_quotes drop column discount_type;

alter table window_quotes drop column transport;
alter table window_quotes drop column format;

delete from flyway_schema_history where version = '10';
