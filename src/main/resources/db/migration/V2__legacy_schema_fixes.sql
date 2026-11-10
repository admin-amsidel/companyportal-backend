-- Copyright (c) 2026 AMSIDEL Technologies. All rights reserved.
--
-- V2: what the SchemaFixes start-up runner did on every start under ddl-auto=update, now done once by Flyway.
-- Every step checks first, so it is safe on the production database (where most of it was done already) and a no-op
-- on a new one made by V1.
--
-- From here on, ddl-auto=validate makes no schema changes: a new column, table, wider column or a new value of an
-- enum column (its CHECK lists the allowed values) needs its own V<n>__what.sql.

-- Helper for this script only: lets a column's CHECK allow exactly these enum values (dropped at the end).
create or replace function cp_v2_enum_check(p_table text, p_column text, p_values text[]) returns void
language plpgsql as $$
declare
    c record;
    current_ok boolean := false;
    list text;
begin
    if to_regclass(p_table) is null then
        return;
    end if;
    -- current already when one check on the column names every value
    select bool_or(not exists (select 1 from unnest(p_values) v where pg_get_constraintdef(con.oid) not like '%''' || v || '''%'))
      into current_ok
      from pg_constraint con join pg_class rel on rel.oid = con.conrelid
     where rel.relname = p_table and con.contype = 'c' and pg_get_constraintdef(con.oid) like '%' || p_column || '%';
    if coalesce(current_ok, false) then
        return;
    end if;
    for c in select con.conname from pg_constraint con join pg_class rel on rel.oid = con.conrelid
              where rel.relname = p_table and con.contype = 'c' and pg_get_constraintdef(con.oid) like '%' || p_column || '%'
    loop
        execute format('alter table %I drop constraint if exists %I', p_table, c.conname);
    end loop;
    select string_agg(quote_literal(v), ',') into list from unnest(p_values) v;
    execute format('alter table %I add constraint %I check (%I in (%s))', p_table, 'ck_' || p_table || '_' || p_column, p_column, list);
    raise notice '%.% now allows %', p_table, p_column, list;
end $$;

-- 1. Field days have several attendance entries a day (uk_attendance_entry); the first one-a-day rule goes.
alter table if exists attendance drop constraint if exists uk_attendance_employee_day;

-- 2. Ratings and skill levels are half stars (numeric(3,1)); whole-star values are kept (4 becomes 4.0).
do $$
declare
    col text[];
begin
    foreach col slice 1 in array array[
            ['goals', 'self_rating'], ['goals', 'manager_rating'],
            ['review_skills', 'self_level'], ['review_skills', 'manager_level']]
    loop
        if exists (select 1 from information_schema.columns
                    where table_schema = current_schema() and table_name = col[1] and column_name = col[2] and data_type like '%int%') then
            execute format('alter table %I alter column %I type numeric(3,1)', col[1], col[2]);
        end if;
    end loop;
end $$;

-- 3. Enum columns whose table was made before the enum grew: their CHECK allows every current value.
select cp_v2_enum_check('trade_history', 'subject_type', array['QUOTATION','SALES_ORDER','INVOICE','PURCHASE','ITEM','CUSTOMER','SUPPLIER','PRODUCT_TYPE','PROJECT','PROJECT_TASK','MONEY_ENTRY','BANK_STATEMENT','BANK_BALANCE']);
select cp_v2_enum_check('news_posts', 'audience', array['EMPLOYEES','CUSTOMERS','PUBLIC','EVERYONE']);
select cp_v2_enum_check('online_payments', 'method', array['CARD','UPI','MANUAL_UPI']);
select cp_v2_enum_check('online_fees', 'method', array['CARD','UPI','MANUAL_UPI']);
select cp_v2_enum_check('brands', 'currency', array['INR','USD','EUR','GBP','JPY','AED','AUD','NZD','CNY','CHF','CAD']);
select cp_v2_enum_check('stock_moves', 'kind', array['OPENING','PURCHASE','PURCHASE_CANCEL','SALE','SALE_CANCEL','ADJUST_IN','ADJUST_OUT','RETURN_OUT','RETURN_IN']);
select cp_v2_enum_check('sales_docs', 'kind', array['QUOTATION','SALES_ORDER','INVOICE']);
select cp_v2_enum_check('goals', 'goal_type', array['GENERAL','SALES','SALES_ORDER','TASK_WORKLOG','MANUAL_SALES']);

-- 4. Columns that held an enum and now hold a code from a table: their old allowed-values CHECK goes.
do $$
declare
    col text[];
    c record;
begin
    foreach col slice 1 in array array[['money_entries', 'category'], ['projects', 'status'], ['project_updates', 'status']]
    loop
        for c in select con.conname from pg_constraint con join pg_class rel on rel.oid = con.conrelid
                  where rel.relname = col[1] and con.contype = 'c' and pg_get_constraintdef(con.oid) like '%' || col[2] || '%'
        loop
            execute format('alter table %I drop constraint if exists %I', col[1], c.conname);
        end loop;
    end loop;
end $$;

-- 5. Columns made optional after they were created as required (manual UPI has no Razorpay order, documents moved
--    into the database, check-ins without a photo).
do $$
declare
    col text[];
begin
    foreach col slice 1 in array array[
            ['online_payments', 'gateway_key_id'], ['online_payments', 'gateway_order_id'],
            ['attendance', 'photo_path'], ['employee_documents', 'stored_path']]
    loop
        if exists (select 1 from information_schema.columns
                    where table_schema = current_schema() and table_name = col[1] and column_name = col[2] and is_nullable = 'NO') then
            execute format('alter table %I alter column %I drop not null', col[1], col[2]);
        end if;
    end loop;
end $$;

-- 6. Status columns that now hold longer codes (30 characters).
do $$
declare
    col text[];
begin
    foreach col slice 1 in array array[['projects', 'status'], ['project_updates', 'status']]
    loop
        if exists (select 1 from information_schema.columns
                    where table_schema = current_schema() and table_name = col[1] and column_name = col[2]
                      and character_maximum_length < 30) then
            execute format('alter table %I alter column %I type varchar(30)', col[1], col[2]);
        end if;
    end loop;
end $$;

-- 7. News saved as "customers" when that meant the website becomes Public, once (marked in doc_sequences as before).
do $$
begin
    if to_regclass('doc_sequences') is not null and to_regclass('news_posts') is not null
       and not exists (select 1 from doc_sequences where prefix = 'SETUP-NEWS-PUBLIC') then
        update news_posts set audience = 'PUBLIC' where audience = 'CUSTOMERS';
        insert into doc_sequences (prefix, last_number) values ('SETUP-NEWS-PUBLIC', 1);
    end if;
end $$;

-- 8. Designation names and levels are unique per brand now, not in the whole group: the old one-column rules go.
do $$
declare
    c record;
begin
    for c in select con.conname from pg_constraint con join pg_class rel on rel.oid = con.conrelid
              where rel.relname = 'designations' and con.contype = 'u'
                and pg_get_constraintdef(con.oid) in ('UNIQUE (name)', 'UNIQUE (level_code)')
    loop
        execute format('alter table designations drop constraint if exists %I', c.conname);
    end loop;
end $$;

drop function cp_v2_enum_check(text, text, text[]);
