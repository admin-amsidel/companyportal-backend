-- Copyright (c) 2026 IANEL Technologies. All rights reserved.
--
-- Runs after every Flyway migrate (each backend start), so it also covers tables added by later migrations.
--
-- Row Level Security on every table of the portal's schema (${flyway:defaultSchema}), with NO policies: Supabase's Data
-- API (PostgREST, reached with the project's anon or authenticated key) can then read and change nothing in these tables,
-- even if the schema is ever added to the API's list. The portal does not use that API: the backend connects as the
-- tables' owner (postgres), and an owner is not held back by RLS (it is not FORCEd), so the portal works as before.
--
-- A table this login does not own cannot be changed here; it is reported and skipped instead of stopping the start.
do $$
declare
    t record;
begin
    for t in
        select c.relname
        from pg_class c
        join pg_namespace n on n.oid = c.relnamespace
        where n.nspname = '${flyway:defaultSchema}'
          and c.relkind in ('r', 'p')
          and not c.relrowsecurity
    loop
        begin
            execute format('alter table %I.%I enable row level security', '${flyway:defaultSchema}', t.relname);
            raise notice 'Row level security switched on for %', t.relname;
        exception when insufficient_privilege then
            raise warning 'Row level security NOT switched on for % (not owned by this login)', t.relname;
        end;
    end loop;
end $$;
