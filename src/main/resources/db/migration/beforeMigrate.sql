-- Copyright (c) 2026 IANEL Technologies. All rights reserved.
--
-- Runs before every Flyway migrate (each backend start). The portal's tables live in the ${flyway:defaultSchema} schema
-- (DB_SCHEMA). A database whose portal data is still in the public schema, from before that move, must not get a new,
-- empty set of tables in ${flyway:defaultSchema} and start with no data: the start stops here with what to do instead.
do $$
begin
    if '${flyway:defaultSchema}' <> 'public'
       and not exists (select 1 from pg_tables where schemaname = '${flyway:defaultSchema}' and tablename = 'employees')
       and exists (select 1 from pg_tables where schemaname = 'public' and tablename = 'employees') then
        raise exception 'The portal data is in the public schema, but the app now uses the "${flyway:defaultSchema}" schema, which has none. Move the data first: DbBackupTool, back up public, then restore into ${flyway:defaultSchema} (see the backend README).';
    end if;
end $$;
