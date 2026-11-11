/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Backs up the portal's tables (one schema: every table's structure and data) and restores them into a schema of
 * another (or the same) PostgreSQL, e.g. moving to a new Supabase project, without pg_dump. The backup names no schema,
 * so a backup of {@code public} can be restored into {@code portal}. Never runs in a normal build.
 * <ul>
 *   <li><b>Inspect</b>, read only: {@code ./mvnw test -Dtest=DbBackupTool -Ddb.inspect=true -Ddb.url=... -Ddb.user=...}</li>
 *   <li><b>Back up</b>, read only: {@code -Ddb.backup=../db-backups -Ddb.url=... -Ddb.user=...} writes a folder with
 *   schema.sql (types, sequences, tables, keys, checks, foreign keys, indexes, RLS), data/&lt;table&gt;.copy (each table
 *   in PostgreSQL's COPY text format: exact, binary data included), sequences.sql and manifest.tsv (tables in load
 *   order with their row counts).</li>
 *   <li><b>Restore</b>: {@code -Ddb.restore=<backup folder> -Ddb.url=<new database> -Ddb.user=...} into a schema with
 *   no tables. By default Flyway makes the tables from the app's migrations (as on the backend's first start, without
 *   its starter data) and the backup's data is loaded into them, with every row checked by the foreign keys and every
 *   table's row count against the backup, all or nothing ({@link Mode}). {@code -Ddb.restore.mode=exact} recreates the
 *   backup's own tables instead; {@code -Ddb.restore.mode=data-only} loads into existing, empty tables.</li>
 * </ul>
 * The schema is {@code -Ddb.schema} (default {@code portal}, the app's DB_SCHEMA): the one backed up, or restored into.
 * The password comes from {@code -Ddb.password}, else {@code spring.datasource.password} in secrets.properties, so it is
 * never typed on the command line or printed.
 */
class DbBackupTool {

    static final String DEFAULT_SCHEMA = "portal";

    // ------------------------------------------------------------------ inspect

    @Test
    @EnabledIfSystemProperty(named = "db.inspect", matches = "true")
    void inspect() throws Exception {
        String ns = schema();
        try (Connection c = connect()) {
            c.setReadOnly(true);
            System.out.println("Server: " + one(c, "select version()"));
            System.out.println("Logged in as: " + one(c, "select current_user"));
            System.out.println("Database size: " + one(c, "select pg_size_pretty(pg_database_size(current_database()))"));
            for (Map<String, String> r : rows(c, """
                    select n.nspname as schema, count(c.oid) as tables from pg_namespace n
                    left join pg_class c on c.relnamespace = n.oid and c.relkind in ('r', 'p')
                    where n.nspname not like 'pg\\_%' and n.nspname <> 'information_schema'
                    group by n.nspname having count(c.oid) > 0 order by 1""")) {
                System.out.println("Schema " + r.get("schema") + ": " + r.get("tables") + " tables");
            }
            List<String> tables = tables(c, ns);
            long rows = 0;
            System.out.printf("%n%-40s %10s %10s %s%n", "TABLE IN " + ns, "ROWS", "SIZE", "OWNER");
            for (String t : tables) {
                long n = count(c, ns, t);
                rows += n;
                System.out.printf("%-40s %10d %10s %s%n", t, n,
                        one(c, "select pg_size_pretty(pg_total_relation_size('" + quote(ns) + "." + quote(t) + "'))"),
                        one(c, "select tableowner from pg_tables where schemaname = " + lit(ns) + " and tablename = " + lit(t)));
            }
            System.out.println(tables.size() + " tables, " + rows + " rows in " + ns);
            others(c, ns).forEach(o -> System.out.println("NOT TABLE DATA (check by hand): " + o));
        }
    }

    // ------------------------------------------------------------------ backup

    @Test
    @EnabledIfSystemProperty(named = "db.backup", matches = ".+")
    void backup() throws Exception {
        try (Connection c = connect()) {
            backupTo(c, schema(), Path.of(System.getProperty("db.backup")), redact(System.getProperty("db.url")));
        }
    }

    /** Writes a new backup-&lt;time&gt; folder under parent with this schema's tables; returns it. Read only. */
    static Path backupTo(Connection c, String ns, Path parent, String source) throws SQLException, IOException {
        Path dir = parent.resolve("backup-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss"))).toAbsolutePath();
        Files.createDirectories(dir.resolve("data"));
        c.setReadOnly(true);
        // one consistent picture of the whole database, however long the copy takes
        c.setAutoCommit(false);
        try (Statement s = c.createStatement()) {
            s.execute("set transaction isolation level repeatable read, read only");
            // names come out without the schema, so the backup can go into any schema
            s.execute("set local search_path to " + quote(ns));
        }
        List<String> tables = loadOrder(c, ns, tables(c, ns));
        if (tables.isEmpty()) throw new IllegalStateException("No tables in the schema " + ns);
        Files.writeString(dir.resolve("schema.sql"), schemaSql(c, ns, tables));
        Files.writeString(dir.resolve("sequences.sql"), sequencesSql(c, ns));

        CopyManager copy = c.unwrap(PGConnection.class).getCopyAPI();
        StringBuilder manifest = new StringBuilder("# table\trows\tcolumns (load order: parents before children)\n");
        long total = 0;
        for (String t : tables) {
            List<String> cols = copyColumns(c, ns, t);
            long n;
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(dir.resolve("data").resolve(t + ".copy")))) {
                n = copy.copyOut("copy " + quote(ns) + "." + quote(t) + " (" + joinQuoted(cols) + ") to stdout", out);
            }
            total += n;
            manifest.append(t).append('\t').append(n).append('\t').append(String.join(",", cols)).append('\n');
            System.out.printf("%-40s %10d rows%n", t, n);
        }
        List<String> others = others(c, ns);
        if (!others.isEmpty()) {
            manifest.append("# NOT IN THIS BACKUP (not table data): ").append(String.join("; ", others)).append('\n');
        }
        Files.writeString(dir.resolve("manifest.tsv"), manifest.toString());
        Files.writeString(dir.resolve("README.txt"), """
                Company Portal database backup, %s
                Source: %s, schema %s (%s)
                %d tables, %d rows. Contains personal and salary data: keep it private, do not commit it to git.

                schema.sql     tables, keys, checks, foreign keys, indexes, row level security (no schema named)
                data/*.copy    each table's rows (PostgreSQL COPY text format)
                sequences.sql  where each ID counter stands
                manifest.tsv   load order, row counts, columns

                Restore into a schema with no tables (default: portal) - see DbBackupTool in companyportal-backend.
                Flyway makes the tables from the app's migrations, then this data is loaded into them:
                  ./mvnw test -Dtest=DbBackupTool -Ddb.restore=<this folder> -Ddb.schema=portal -Ddb.url=<new database JDBC URL> -Ddb.user=<user> -Ddb.password=<password>
                """.formatted(LocalDateTime.now(), source, ns, one(c, "select version()"), tables.size(), total));
        c.commit();
        System.out.println("Backup of schema " + ns + " written to " + dir + " (" + tables.size() + " tables, " + total + " rows)");
        others.forEach(o -> System.out.println("NOT IN THE BACKUP (check by hand): " + o));
        return dir;
    }

    // ------------------------------------------------------------------ restore

    /** How a restore makes the tables. */
    enum Mode {
        /** Flyway makes the schema from the app's migrations, then only the data is loaded (the default). */
        FLYWAY,
        /** The schema exactly as in the backup, then its data (a copy of the same version, Flyway history included). */
        EXACT,
        /** Only the data, into tables that exist already and are empty. */
        DATA_ONLY
    }

    @Test
    @EnabledIfSystemProperty(named = "db.restore", matches = ".+")
    void restore() throws Exception {
        Mode mode = Boolean.getBoolean("db.restore.data-only") ? Mode.DATA_ONLY
                : Mode.valueOf(System.getProperty("db.restore.mode", "flyway").toUpperCase(java.util.Locale.ROOT).replace('-', '_'));
        Path dir = Path.of(System.getProperty("db.restore")).toAbsolutePath();
        if (mode == Mode.FLYWAY) {
            migrate(new org.springframework.jdbc.datasource.DriverManagerDataSource(System.getProperty("db.url"),
                    System.getProperty("db.user", "postgres"), password()), schema());
        }
        try (Connection c = connect()) {
            restoreFrom(c, schema(), dir, mode == Mode.EXACT ? Mode.EXACT : Mode.DATA_ONLY);
        }
    }

    /**
     * Makes the schema with the app's own Flyway migrations and settings (as the backend does on its first start, but
     * without its starter data), so the data of a backup can then be loaded. The schema must have no tables yet.
     */
    static int migrate(javax.sql.DataSource db, String ns) throws SQLException {
        try (Connection c = db.getConnection()) {
            if (!tables(c, ns).isEmpty()) {
                throw new IllegalStateException("The schema " + ns + " already has tables: Flyway mode makes them itself");
            }
        }
        int n = org.flywaydb.core.Flyway.configure().dataSource(db).locations("classpath:db/migration")
                .schemas(ns).defaultSchema(ns).createSchemas(true).baselineOnMigrate(false).load().migrate().migrationsExecuted;
        System.out.println("Flyway made the schema " + ns + " (" + n + " migrations)");
        return n;
    }

    /**
     * Loads a backup folder into this schema; all or nothing, with every table's row count checked.
     * <ul>
     *   <li>EXACT: the schema must have no tables; it gets the backup's own tables (and Flyway history), then the data.</li>
     *   <li>DATA_ONLY: the tables exist (e.g. made by Flyway) and are empty. The backup's Flyway history is left out (the
     *   schema has its own), foreign keys are set aside while loading and put back after (so they check every row),
     *   a column the schema has and the backup does not gets its default, a column the backup has and the schema no
     *   longer has is reported and left out, and every ID counter goes on after the highest ID loaded.</li>
     * </ul>
     */
    static void restoreFrom(Connection c, String ns, Path dir, Mode mode) throws SQLException, IOException {
        if (mode == Mode.FLYWAY) throw new IllegalArgumentException("Run migrate first, then restore DATA_ONLY");
        boolean dataOnly = mode == Mode.DATA_ONLY;
        Map<String, Long> expected = new LinkedHashMap<>();
        Map<String, String> columns = new LinkedHashMap<>();
        for (String line : Files.readAllLines(dir.resolve("manifest.tsv"))) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] p = line.split("\t", 3);
            if (dataOnly && p[0].equals(FLYWAY_HISTORY)) continue;
            expected.put(p[0], Long.parseLong(p[1]));
            columns.put(p[0], p[2]);
        }
        String schemaSql = Files.readString(dir.resolve("schema.sql"));
        if (schemaSql.contains("create table public.")) {
            throw new IllegalStateException("This backup was made by an older DbBackupTool that names the public schema;"
                    + " take a new backup to restore into " + ns);
        }
        String[] schema = schemaSql.split("(?m)^-- @@AFTER_DATA@@$", 2);

        c.setAutoCommit(false);
        System.out.println("Restoring (" + mode + ") into schema " + ns + " of " + one(c, "select version()") + " as " + one(c, "select current_user"));
        try (Statement s = c.createStatement()) {
            s.execute("create schema if not exists " + quote(ns));
            s.execute("set local search_path to " + quote(ns));
        }
        List<String> foreignKeys = new ArrayList<>();
        if (!dataOnly) {
            if (!tables(c, ns).isEmpty()) {
                throw new IllegalStateException("The schema " + ns + " already has tables: restore into an empty one");
            }
            run(c, schema[0]);
        } else {
            List<String> there = tables(c, ns);
            List<String> missing = expected.keySet().stream().filter(t -> !there.contains(t)).toList();
            if (!missing.isEmpty()) {
                throw new IllegalStateException("The schema " + ns + " has no table for: " + missing + " (their rows would be lost)");
            }
            for (String t : expected.keySet()) {
                if (count(c, ns, t) > 0) throw new IllegalStateException(t + " is not empty in " + ns);
            }
            // set aside, put back after the load: then the order of the tables does not matter, and every row is checked
            for (Map<String, String> k : rows(c, """
                    select cl.relname as tab, k.conname as name, pg_get_constraintdef(k.oid) as def
                    from pg_constraint k join pg_class cl on cl.oid = k.conrelid join pg_namespace n on n.oid = cl.relnamespace
                    where n.nspname = %s and k.contype = 'f'""".formatted(lit(ns)))) {
                foreignKeys.add("alter table " + quote(k.get("tab")) + " add constraint " + quote(k.get("name")) + " " + k.get("def"));
                run(c, "alter table " + quote(k.get("tab")) + " drop constraint " + quote(k.get("name")));
            }
        }
        CopyManager copy = c.unwrap(PGConnection.class).getCopyAPI();
        for (Map.Entry<String, Long> e : expected.entrySet()) {
            String t = e.getKey();
            List<String> cols = List.of(columns.get(t).split(","));
            List<String> have = dataOnly ? copyColumns(c, ns, t) : cols;
            List<String> left = cols.stream().filter(col -> !have.contains(col)).toList();
            try (InputStream in = new BufferedInputStream(Files.newInputStream(dir.resolve("data").resolve(t + ".copy")))) {
                long n;
                if (left.isEmpty()) {
                    n = copy.copyIn("copy " + quote(ns) + "." + quote(t) + " (" + joinQuoted(cols) + ") from stdin", in);
                } else {
                    // the backup has columns the schema dropped: through a scratch table, keeping only those it still has
                    System.out.println("NOTE " + t + ": left out, no longer in the schema: " + left);
                    run(c, "create temp table bk_load on commit drop as select * from " + quote(ns) + "." + quote(t) + " with no data");
                    for (String col : left) run(c, "alter table bk_load add column " + quote(col) + " text");
                    n = copy.copyIn("copy bk_load (" + joinQuoted(cols) + ") from stdin", in);
                    List<String> kept = cols.stream().filter(col -> !left.contains(col)).toList();
                    run(c, "insert into " + quote(ns) + "." + quote(t) + " (" + joinQuoted(kept) + ") select " + joinQuoted(kept) + " from bk_load");
                    run(c, "drop table bk_load");
                }
                System.out.printf("%-40s %10d rows%n", t, n);
            }
        }
        if (!dataOnly) {
            run(c, schema[1]);
            run(c, Files.readString(dir.resolve("sequences.sql")));
        } else {
            for (String fk : foreignKeys) run(c, fk);
            restartCounters(c, ns);
        }
        List<String> wrong = new ArrayList<>();
        for (Map.Entry<String, Long> e : expected.entrySet()) {
            long n = count(c, ns, e.getKey());
            if (n != e.getValue()) wrong.add(e.getKey() + ": " + n + " rows, the backup has " + e.getValue());
        }
        if (!wrong.isEmpty()) {
            c.rollback();
            throw new IllegalStateException("Row counts differ, nothing was kept: " + wrong);
        }
        c.commit();
        System.out.println("Restored into " + ns + " and checked: " + expected.size() + " tables, "
                + expected.values().stream().mapToLong(Long::longValue).sum() + " rows, every count matches the backup"
                + (dataOnly ? ", foreign keys checked" : ""));
    }

    private static final String FLYWAY_HISTORY = "flyway_schema_history";

    /** Every ID counter (identity or sequence default) goes on after the highest ID now in its table. */
    private static void restartCounters(Connection c, String ns) throws SQLException {
        for (Map<String, String> col : rows(c, """
                select table_name as tab, column_name as col from information_schema.columns
                where table_schema = %s and (is_identity = 'YES' or column_default like 'nextval(%%')""".formatted(lit(ns)))) {
            String seq = one(c, "select pg_get_serial_sequence(" + lit(quote(ns) + "." + quote(col.get("tab"))) + ", " + lit(col.get("col")) + ")");
            if (seq == null) continue;
            one(c, "select setval(" + lit(seq) + ", coalesce((select max(" + quote(col.get("col")) + ") from " + quote(ns) + "."
                    + quote(col.get("tab")) + "), 0) + 1, false)");
        }
    }

    // ------------------------------------------------------------------ the schema

    /**
     * Types and tables first; after the marker, what goes on once the data is in (foreign keys, indexes, RLS). Read with
     * the search path on the schema, so nothing in it names the schema.
     */
    private static String schemaSql(Connection c, String ns, List<String> tables) throws SQLException {
        StringBuilder before = new StringBuilder("-- Company Portal schema, from DbBackupTool (restored into the schema of the restore)\n\n");
        StringBuilder after = new StringBuilder();
        for (Map<String, String> e : rows(c, """
                select t.typname as name, string_agg(quote_literal(e.enumlabel), ', ' order by e.enumsortorder) as labels
                from pg_type t join pg_enum e on e.enumtypid = t.oid join pg_namespace n on n.oid = t.typnamespace
                where n.nspname = %s group by t.typname order by t.typname""".formatted(lit(ns)))) {
            before.append("create type ").append(quote(e.get("name"))).append(" as enum (").append(e.get("labels")).append(");\n");
        }
        // sequences used by column defaults (identity columns make their own)
        for (Map<String, String> s : rows(c, """
                select c.relname as name from pg_class c join pg_namespace n on n.oid = c.relnamespace
                where n.nspname = %s and c.relkind = 'S'
                  and not exists (select 1 from pg_depend d where d.objid = c.oid and d.deptype = 'i') order by c.relname""".formatted(lit(ns)))) {
            before.append("create sequence ").append(quote(s.get("name"))).append(";\n");
        }
        for (String t : tables) {
            long oid = Long.parseLong(one(c, "select '" + quote(ns).replace("'", "''") + "." + quote(t).replace("'", "''") + "'::regclass::oid"));
            List<String> lines = new ArrayList<>();
            for (Map<String, String> col : rows(c, """
                    select a.attname as name, format_type(a.atttypid, a.atttypmod) as type, a.attnotnull::text as notnull,
                           pg_get_expr(d.adbin, d.adrelid) as def, a.attidentity::text as ident, a.attgenerated::text as gen
                    from pg_attribute a left join pg_attrdef d on d.adrelid = a.attrelid and d.adnum = a.attnum
                    where a.attrelid = %d and a.attnum > 0 and not a.attisdropped order by a.attnum""".formatted(oid))) {
                StringBuilder l = new StringBuilder("    ").append(quote(col.get("name"))).append(' ').append(col.get("type"));
                if ("s".equals(col.get("gen"))) {
                    l.append(" generated always as (").append(col.get("def")).append(") stored");
                } else if ("d".equals(col.get("ident"))) {
                    l.append(" generated by default as identity");
                } else if ("a".equals(col.get("ident"))) {
                    l.append(" generated always as identity");
                } else if (col.get("def") != null) {
                    l.append(" default ").append(col.get("def"));
                }
                if ("true".equals(col.get("notnull"))) l.append(" not null");
                lines.add(l.toString());
            }
            for (Map<String, String> k : rows(c, """
                    select conname as name, pg_get_constraintdef(oid) as def from pg_constraint
                    where conrelid = %d and contype in ('p', 'u', 'c', 'x') order by contype desc, conname""".formatted(oid))) {
                lines.add("    constraint " + quote(k.get("name")) + " " + k.get("def"));
            }
            before.append("\ncreate table ").append(quote(t)).append(" (\n").append(String.join(",\n", lines)).append("\n);\n");
            if ("true".equals(one(c, "select relrowsecurity::text from pg_class where oid = " + oid))) {
                after.append("alter table ").append(quote(t)).append(" enable row level security;\n");
            }
        }
        for (Map<String, String> k : rows(c, """
                select cl.relname as tab, k.conname as name, pg_get_constraintdef(k.oid) as def
                from pg_constraint k join pg_class cl on cl.oid = k.conrelid join pg_namespace n on n.oid = cl.relnamespace
                where n.nspname = %s and k.contype = 'f' order by cl.relname, k.conname""".formatted(lit(ns)))) {
            after.append("alter table ").append(quote(k.get("tab"))).append(" add constraint ").append(quote(k.get("name")))
                    .append(' ').append(k.get("def")).append(";\n");
        }
        for (Map<String, String> i : rows(c, """
                select pg_get_indexdef(i.indexrelid) as def
                from pg_index i join pg_class cl on cl.oid = i.indrelid join pg_namespace n on n.oid = cl.relnamespace
                where n.nspname = %s and cl.relkind in ('r', 'p')
                  and not exists (select 1 from pg_constraint k where k.conindid = i.indexrelid) order by 1""".formatted(lit(ns)))) {
            // pg_get_indexdef always names the table with its schema ("ON portal.people"): without it, it follows the restore
            String nsIdent = one(c, "select quote_ident(" + lit(ns) + ")");
            after.append(i.get("def").replaceFirst(" ON (ONLY )?" + java.util.regex.Pattern.quote(nsIdent) + "\\.", " ON $1"))
                    .append(";\n");
        }
        String all = before + "\n-- @@AFTER_DATA@@\n" + after;
        // anything still naming the source schema would point back at it
        for (String line : all.split("\n")) {
            if (line.contains(quote(ns) + ".") || line.matches(".*[\\s'(]" + java.util.regex.Pattern.quote(ns) + "\\..*")) {
                throw new IllegalStateException("The schema came out naming " + ns + ", so it cannot move to another schema: " + line.trim());
            }
        }
        return all;
    }

    /** Where every ID counter stands, to set after the data is in (identity columns and plain sequences). */
    private static String sequencesSql(Connection c, String ns) throws SQLException {
        StringBuilder out = new StringBuilder();
        for (Map<String, String> s : rows(c, """
                select c.relname as name from pg_class c join pg_namespace n on n.oid = c.relnamespace
                where n.nspname = %s and c.relkind = 'S' order by c.relname""".formatted(lit(ns)))) {
            Map<String, String> v = rows(c, "select last_value::text as last, is_called::text as called from " + quote(ns) + "." + quote(s.get("name"))).getFirst();
            out.append("select setval(").append(lit(quote(s.get("name")))).append(", ").append(v.get("last")).append(", ")
                    .append(v.get("called")).append(");\n");
        }
        return out.toString();
    }

    /** Parents before children, so a load with foreign keys on still works (a table pointing at itself is fine). */
    private static List<String> loadOrder(Connection c, String ns, List<String> tables) throws SQLException {
        Map<String, Set<String>> parents = new LinkedHashMap<>();
        tables.forEach(t -> parents.put(t, new LinkedHashSet<>()));
        for (Map<String, String> f : rows(c, """
                select ch.relname as child, pa.relname as parent from pg_constraint k
                join pg_class ch on ch.oid = k.conrelid join pg_class pa on pa.oid = k.confrelid
                join pg_namespace n on n.oid = ch.relnamespace
                where n.nspname = %s and k.contype = 'f' and ch.oid <> pa.oid""".formatted(lit(ns)))) {
            if (parents.containsKey(f.get("child")) && parents.containsKey(f.get("parent"))) {
                parents.get(f.get("child")).add(f.get("parent"));
            }
        }
        List<String> order = new ArrayList<>();
        Set<String> done = new LinkedHashSet<>();
        boolean moved = true;
        while (order.size() < tables.size() && moved) {
            moved = false;
            for (String t : tables) {
                if (!done.contains(t) && done.containsAll(parents.get(t))) {
                    order.add(t);
                    done.add(t);
                    moved = true;
                }
            }
        }
        // a loop of foreign keys: the rest in name order (the full restore adds foreign keys after the data anyway)
        tables.stream().filter(t -> !done.contains(t)).forEach(order::add);
        return order;
    }

    /** What the schema holds besides tables: not in this backup, so it is reported. */
    private static List<String> others(Connection c, String ns) throws SQLException {
        List<String> out = new ArrayList<>();
        for (Map<String, String> r : rows(c, """
                select 'view ' || relname as what from pg_class c join pg_namespace n on n.oid = c.relnamespace
                where n.nspname = %1$s and relkind in ('v', 'm', 'f')
                union all select 'function ' || p.proname from pg_proc p join pg_namespace n on n.oid = p.pronamespace
                where n.nspname = %1$s
                union all select 'trigger ' || t.tgname || ' on ' || c.relname from pg_trigger t join pg_class c on c.oid = t.tgrelid
                join pg_namespace n on n.oid = c.relnamespace where n.nspname = %1$s and not t.tgisinternal
                union all select 'policy ' || policyname || ' on ' || tablename from pg_policies where schemaname = %1$s""".formatted(lit(ns)))) {
            out.add(r.get("what"));
        }
        return out;
    }

    private static List<String> copyColumns(Connection c, String ns, String table) throws SQLException {
        List<String> cols = new ArrayList<>();
        for (Map<String, String> r : rows(c, """
                select a.attname as name from pg_attribute a where a.attrelid = %s::regclass and a.attnum > 0
                and not a.attisdropped and a.attgenerated = '' order by a.attnum""".formatted(lit(quote(ns) + "." + quote(table))))) {
            cols.add(r.get("name"));
        }
        return cols;
    }

    // ------------------------------------------------------------------ plumbing

    private static String schema() {
        return System.getProperty("db.schema", DEFAULT_SCHEMA);
    }

    private static Connection connect() throws SQLException, IOException {
        String url = System.getProperty("db.url");
        if (url == null || url.isBlank()) throw new IllegalArgumentException("Give -Ddb.url=jdbc:postgresql://...");
        Properties p = new Properties();
        p.setProperty("user", System.getProperty("db.user", "postgres"));
        p.setProperty("password", password());
        // the session pooler would otherwise cache plans between COPY commands
        p.setProperty("prepareThreshold", "0");
        return DriverManager.getConnection(url, p);
    }

    private static String password() throws IOException {
        String given = System.getProperty("db.password");
        if (given != null) return given;
        Path secrets = Path.of("secrets.properties");
        if (Files.exists(secrets)) {
            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(secrets)) {
                p.load(in);
            }
            String v = p.getProperty("spring.datasource.password");
            if (v != null) return v;
        }
        throw new IllegalArgumentException("No password: give -Ddb.password=... or set spring.datasource.password in secrets.properties");
    }

    private static List<String> tables(Connection c, String ns) throws SQLException {
        List<String> out = new ArrayList<>();
        for (Map<String, String> r : rows(c, "select tablename as name from pg_tables where schemaname = " + lit(ns) + " order by tablename")) {
            out.add(r.get("name"));
        }
        return out;
    }

    private static long count(Connection c, String ns, String table) throws SQLException {
        return Long.parseLong(one(c, "select count(*) from " + quote(ns) + "." + quote(table)));
    }

    private static List<Map<String, String>> rows(Connection c, String sql) throws SQLException {
        List<Map<String, String>> out = new ArrayList<>();
        try (PreparedStatement s = c.prepareStatement(sql); ResultSet r = s.executeQuery()) {
            int n = r.getMetaData().getColumnCount();
            while (r.next()) {
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 1; i <= n; i++) row.put(r.getMetaData().getColumnLabel(i), r.getString(i));
                out.add(row);
            }
        }
        return out;
    }

    private static String one(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
            return r.next() ? r.getString(1) : null;
        }
    }

    private static void run(Connection c, String sql) throws SQLException {
        for (String part : sql.split(";\\s*\\n")) {
            String stmt = part.lines().filter(l -> !l.startsWith("--")).reduce("", (a, b) -> a + "\n" + b).trim();
            if (stmt.isEmpty()) continue;
            try (Statement s = c.createStatement()) {
                s.execute(stmt);
            } catch (SQLException e) {
                throw new SQLException("Failed: " + stmt.lines().findFirst().orElse(stmt) + " - " + e.getMessage(), e);
            }
        }
    }

    /** A setting the login may not be allowed to change: tried in a savepoint, so a refusal does not end the restore. */
    private static void trySet(Connection c, String sql) throws SQLException {
        java.sql.Savepoint sp = c.setSavepoint();
        try (Statement s = c.createStatement()) {
            s.execute(sql);
            c.releaseSavepoint(sp);
        } catch (SQLException e) {
            c.rollback(sp);
            System.out.println("(not allowed here, loading in foreign-key order instead: " + e.getMessage() + ")");
        }
    }

    static String quote(String name) {
        return '"' + name.replace("\"", "\"\"") + '"';
    }

    private static String lit(String s) {
        return "'" + s.replace("'", "''") + "'";
    }

    private static String joinQuoted(List<String> names) {
        return String.join(", ", names.stream().map(DbBackupTool::quote).toList());
    }

    private static String redact(String url) {
        return url == null ? "" : url.replaceAll("password=[^&]*", "password=***");
    }
}
