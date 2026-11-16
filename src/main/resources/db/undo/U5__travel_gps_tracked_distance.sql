-- Copyright (c) 2026 IANEL Technologies. All rights reserved.
--
-- UNDO of V5__travel_gps_tracked_distance.sql: GPS claims lose the route as tracked (the current route and the claimed
-- distance stay), and V5 leaves Flyway's history. Never run at start-up: run with FlywayRevertTool (backend README,
-- "Reverting the last migration"), after deploying a backend without the V5 change.
-- By hand in the Supabase SQL editor instead: run "set search_path to portal;" first.

alter table travel_claims drop column if exists gps_longest_km;
alter table travel_claims drop column if exists gps_shortest_km;

delete from flyway_schema_history where version = '5';
