/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.tools;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.hibernate.cfg.AvailableSettings;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The migration scripts on a real PostgreSQL, in the app's own schema ({@value #SCHEMA}, DB_SCHEMA) as the backend runs
 * them, ending with the check the app makes at start-up (ddl-auto=validate: the tables match the entities). A failure
 * here means a migration is missing for an entity change: add the next V&lt;n&gt;__what.sql.
 */
class FlywayMigrationTest {

    /** application.properties: app.db.schema. */
    static final String SCHEMA = "portal";

    private static EmbeddedPostgres pg;

    @BeforeAll
    static void start() throws Exception {
        pg = EmbeddedPostgres.builder().start();
    }

    @AfterAll
    static void stop() throws Exception {
        if (pg != null) {
            pg.close();
        }
    }

    @Test
    void newDatabaseIsMadeByTheMigrations() {
        DataSource db = freshDatabase("fresh");
        MigrateResult result = flyway(db, SCHEMA).migrate();
        assertThat(result.migrationsExecuted).isGreaterThanOrEqualTo(1);
        assertThat(result.migrations.getFirst().version).isEqualTo("1");
        validate("fresh", SCHEMA);
        // running again changes nothing
        assertThat(flyway(db, SCHEMA).migrate().migrationsExecuted).isZero();
        JdbcTemplate jdbc = new JdbcTemplate(db);
        // everything is in the app's schema, history included; nothing in public
        assertThat(jdbc.queryForObject("select count(*) from pg_tables where schemaname = 'portal' and tablename in ('employees', 'flyway_schema_history')", Integer.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from pg_tables where schemaname = 'public'", Integer.class)).isZero();
        rowLevelSecurityIsOnEverywhere(jdbc, SCHEMA);
    }

    @Test
    void dataStillInPublicStopsTheStartInsteadOfOpeningAnEmptyPortal() {
        DataSource db = freshDatabase("still_public");
        JdbcTemplate jdbc = new JdbcTemplate(db);
        // a database from before the move: the tables, with their history, in public
        flyway(db, "public").migrate();
        jdbc.update("insert into brands (code, name, active) values ('DW', 'Acme', true)");

        assertThatThrownBy(() -> flyway(db, SCHEMA).migrate())
                .hasMessageContaining("The portal data is in the public schema")
                .hasMessageContaining("DbBackupTool");
        assertThat(jdbc.queryForObject("select count(*) from pg_tables where schemaname = 'portal' and tablename = 'employees'", Integer.class)).isZero();
    }

    @Test
    void tablesWithoutFlywayHistoryAreRefusedNotTakenAsUpToDate() {
        DataSource db = freshDatabase("no_history");
        new JdbcTemplate(db).execute("create schema portal; create table portal.employees (id bigint primary key)");
        assertThatThrownBy(() -> flyway(db, SCHEMA).migrate()).hasMessageContaining("non-empty schema");
    }

    @Test
    void everyMigrationAfterV1HasAnUndoThatFlywayNeverRuns() throws Exception {
        var resolver = new org.springframework.core.io.support.PathMatchingResourcePatternResolver();
        java.util.Set<String> versions = new java.util.TreeSet<>();
        for (var r : resolver.getResources("classpath:db/migration/V*__*.sql")) {
            versions.add(r.getFilename().replaceFirst("^V(\\d+)__.*", "$1"));
        }
        for (String v : versions) {
            if (!v.equals("1")) {
                assertThat(resolver.getResources("classpath:db/undo/U" + v + "__*.sql")).as("db/undo/U" + v + "__*.sql").hasSize(1);
            }
        }
        // the undo scripts are not where Flyway looks, under any name it would run
        assertThat(resolver.getResources("classpath:db/migration/U*.sql")).isEmpty();
        assertThat(resolver.getResources("classpath:db/migration/R__*.sql")).isEmpty();
        DataSource db = freshDatabase("undo_never_run");
        MigrateResult result = flyway(db, SCHEMA).migrate();
        assertThat(result.migrations).extracting(m -> m.filepath).noneMatch(f -> f.contains("undo"));
        assertThat(result.migrationsExecuted).isEqualTo(versions.size());
    }

    @Test
    void theLatestMigrationRevertsAndCanBeAppliedAgain() throws Exception {
        DataSource db = freshDatabase("revert");
        flyway(db, SCHEMA).migrate();
        JdbcTemplate jdbc = new JdbcTemplate(db);
        String history = "select max(version::int) from portal.flyway_schema_history where version is not null and success";
        int latest = jdbc.queryForObject(history, Integer.class);
        assertThat(latest).isGreaterThanOrEqualTo(2);

        try (var c = db.getConnection()) {
            // only the latest one
            assertThatThrownBy(() -> FlywayRevertTool.revert(c, SCHEMA, "1")).hasMessageContaining("not the latest");
            FlywayRevertTool.revert(c, SCHEMA, String.valueOf(latest));
        }
        assertThat(jdbc.queryForObject(history, Integer.class)).isEqualTo(latest - 1);
        try (var c = db.getConnection()) {
            assertThatThrownBy(() -> FlywayRevertTool.revert(c, SCHEMA, String.valueOf(latest))).hasMessageContaining("not the latest");
        }
        // the next start applies it again, and the app's check passes
        assertThat(flyway(db, SCHEMA).migrate().migrationsExecuted).isEqualTo(1);
        validate("revert", SCHEMA);

        // and every undo script, from the latest down, takes the schema back to V1 (each one checked by reverting it)
        try (var c = db.getConnection()) {
            for (int v = latest; v >= 2; v--) {
                FlywayRevertTool.revert(c, SCHEMA, String.valueOf(v));
            }
        }
        assertThat(jdbc.queryForObject(history, Integer.class)).isEqualTo(1);
        assertThat(flyway(db, SCHEMA).migrate().migrationsExecuted).isEqualTo(latest - 1);
        validate("revert", SCHEMA);
    }

    /** afterMigrate.sql: every table of the app's schema has RLS on (no policies), the app's own tables included. */
    private static void rowLevelSecurityIsOnEverywhere(JdbcTemplate jdbc, String schema) {
        assertThat(jdbc.queryForList("select c.relname from pg_class c join pg_namespace n on n.oid = c.relnamespace"
                + " where n.nspname = ? and c.relkind in ('r', 'p') and not c.relrowsecurity", String.class, schema)).isEmpty();
        assertThat(jdbc.queryForObject("select c.relrowsecurity from pg_class c join pg_namespace n on n.oid = c.relnamespace"
                + " where n.nspname = ? and c.relname = 'employees'", Boolean.class, schema)).isTrue();
        // the owner (the backend's login) still reads and writes as before
        assertThat(jdbc.queryForObject("select count(*) from " + schema + ".employees", Integer.class)).isNotNull();
    }

    /** The same settings as application.properties, in this schema. */
    static Flyway flyway(DataSource db, String schema) {
        return Flyway.configure().dataSource(db).locations("classpath:db/migration")
                .schemas(schema).defaultSchema(schema).createSchemas(true).baselineOnMigrate(false).load();
    }

    private static DataSource freshDatabase(String name) {
        new JdbcTemplate(pg.getPostgresDatabase()).execute("create database " + name);
        return pg.getDatabase("postgres", name);
    }

    /** The app's start-up check, with the app's schema as the default one (hibernate.default_schema). */
    private static void validate(String name, String schema) {
        validateUrl(pg.getJdbcUrl("postgres", name), schema);
    }

    static void validateUrl(String url, String schema) {
        Map<String, Object> s = new HashMap<>();
        s.put(AvailableSettings.DEFAULT_SCHEMA, schema);
        s.put(AvailableSettings.JAKARTA_JDBC_URL, url);
        s.put(AvailableSettings.JAKARTA_JDBC_USER, "postgres");
        s.put(AvailableSettings.JAKARTA_JDBC_PASSWORD, "");
        s.put(AvailableSettings.HBM2DDL_AUTO, "validate");
        SchemaTool.build(s);
    }
}
