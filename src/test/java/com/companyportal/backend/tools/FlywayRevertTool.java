/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * Reverts the LATEST Flyway migration with its undo script, by hand and on purpose; never runs in a normal build, and
 * the backend never runs undo scripts (they live in db/undo, which is not one of Flyway's locations).
 * <p>
 * Every V&lt;n&gt;__what.sql from V2 on has its undo, db/undo/U&lt;n&gt;__what.sql (FlywayMigrationTest checks it). The
 * undo script takes the change back AND deletes V&lt;n&gt; from flyway_schema_history, so the database is as before it.
 * <pre>
 * ./mvnw test -Dtest=FlywayRevertTool -Ddb.revert=2 "-Ddb.url=jdbc:postgresql://...:5432/postgres?sslmode=require" -Ddb.user=... [-Ddb.schema=portal]
 * </pre>
 * It refuses unless that version is the latest one applied, runs the script in the schema in one transaction, and
 * keeps it only if Flyway's history then ends at the version before. The password comes from {@code -Ddb.password},
 * else spring.datasource.password in secrets.properties.
 * <p>
 * First deploy a backend without the reverted change (its start-up check refuses a database that lacks what its
 * entities have); then revert; then take the V&lt;n&gt; script out of db/migration, or the next start applies it again.
 */
class FlywayRevertTool {

    @Test
    @EnabledIfSystemProperty(named = "db.revert", matches = "\\d+")
    void revert() throws Exception {
        String schema = System.getProperty("db.schema", DbBackupTool.DEFAULT_SCHEMA);
        Properties p = new Properties();
        p.setProperty("user", System.getProperty("db.user", "postgres"));
        p.setProperty("password", password());
        try (Connection c = DriverManager.getConnection(System.getProperty("db.url"), p)) {
            revert(c, schema, System.getProperty("db.revert"));
        }
    }

    /** Runs db/undo/U&lt;version&gt;__*.sql in this schema, only if that version is the latest applied; all or nothing. */
    static void revert(Connection c, String schema, String version) throws SQLException, IOException {
        c.setAutoCommit(false);
        try (Statement s = c.createStatement()) {
            s.execute("set local search_path to " + DbBackupTool.quote(schema));
            String latest = latest(s);
            if (!version.equals(latest)) {
                throw new IllegalStateException("V" + version + " is not the latest migration applied in " + schema
                        + " (the latest is V" + latest + "): only the latest can be reverted, one at a time");
            }
            s.execute(undoScript(version));
            String now = latest(s);
            if (now != null && Integer.parseInt(now) >= Integer.parseInt(version)) {
                c.rollback();
                throw new IllegalStateException("After U" + version + " Flyway's history still ends at V" + now
                        + ": the undo script must delete its version's row; nothing was changed");
            }
            c.commit();
            System.out.println("Reverted V" + version + " in " + schema + "; Flyway's history now ends at V" + now
                    + ". Take V" + version + " out of db/migration before the next start, or it is applied again.");
        } catch (SQLException | RuntimeException e) {
            c.rollback();
            throw e;
        }
    }

    /** The undo script for a version, from db/undo on the classpath. */
    static String undoScript(String version) throws IOException {
        Resource[] found = new PathMatchingResourcePatternResolver().getResources("classpath:db/undo/U" + version + "__*.sql");
        if (found.length != 1) {
            throw new IllegalStateException("Expected one db/undo/U" + version + "__*.sql, found " + found.length);
        }
        return found[0].getContentAsString(StandardCharsets.UTF_8);
    }

    private static String latest(Statement s) throws SQLException {
        try (ResultSet r = s.executeQuery("select version from flyway_schema_history where success and version is not null"
                + " order by installed_rank desc limit 1")) {
            return r.next() ? r.getString(1) : null;
        }
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
}
