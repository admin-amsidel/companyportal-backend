/*
 * Copyright (c) 2026 AMSIDEL Technologies. All rights reserved.
 * Designed and developed by AMSIDEL Technologies.
 */
package com.companyportal.backend.tools;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.hibernate.cfg.AvailableSettings;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The migration scripts on a real PostgreSQL, the two ways they run in life, each ending with the check the app makes
 * at start-up (ddl-auto=validate: the tables match the entities):
 * <ul>
 *   <li>a new, empty database: V1 makes every table, then V2 onwards;</li>
 *   <li>the production database: tables already made by the old ddl-auto=update, some still in their old shape. Flyway
 *   records it as V1 (baseline) and V2 repairs it.</li>
 * </ul>
 * A failure here means a migration is missing for an entity change: add V&lt;n&gt;__what.sql.
 */
class FlywayMigrationTest {

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
        MigrateResult result = flyway(db).migrate();
        assertThat(result.migrationsExecuted).isGreaterThanOrEqualTo(2);
        validate("fresh");
        // running again changes nothing
        assertThat(flyway(db).migrate().migrationsExecuted).isZero();
    }

    @Test
    void productionDatabaseIsBaselinedAndRepaired() throws Exception {
        DataSource db = freshDatabase("legacy");
        JdbcTemplate jdbc = new JdbcTemplate(db);
        // tables as ddl-auto=update left them (no Flyway history yet) ...
        String v1 = new ClassPathResource("db/migration/V1__baseline_schema.sql").getContentAsString(StandardCharsets.UTF_8);
        jdbc.execute(v1);
        // ... with the old shapes that SchemaFixes used to repair on every start
        jdbc.execute("alter table goals alter column self_rating type integer");
        jdbc.execute("alter table review_skills alter column manager_level type integer");
        jdbc.execute("alter table projects alter column status type varchar(20)");
        jdbc.execute("alter table online_payments alter column gateway_order_id set not null");
        dropChecks(jdbc, "news_posts", "audience");
        jdbc.execute("alter table news_posts add constraint news_posts_audience_check check (audience in ('EMPLOYEES','CUSTOMERS'))");
        dropChecks(jdbc, "trade_history", "subject_type");
        jdbc.execute("alter table trade_history add constraint trade_history_subject_type_check check (subject_type in ('QUOTATION','INVOICE'))");
        jdbc.execute("alter table designations add constraint uk_old_designation_name unique (name)");

        MigrateResult result = flyway(db).migrate();

        assertThat(result.initialSchemaVersion).isEqualTo("1");
        assertThat(result.migrations).extracting(m -> m.version).doesNotContain("1").contains("2");
        assertThat(jdbc.queryForObject("select data_type from information_schema.columns where table_name = 'goals' and column_name = 'self_rating'", String.class))
                .isEqualTo("numeric");
        assertThat(jdbc.queryForObject("select character_maximum_length from information_schema.columns where table_name = 'projects' and column_name = 'status'", Integer.class))
                .isEqualTo(30);
        assertThat(jdbc.queryForObject("select is_nullable from information_schema.columns where table_name = 'online_payments' and column_name = 'gateway_order_id'", String.class))
                .isEqualTo("YES");
        // the widened check takes a value the old one refused
        assertThat(jdbc.queryForObject("select count(*) from pg_constraint where conname = 'ck_news_posts_audience' and pg_get_constraintdef(oid) like '%EVERYONE%'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from pg_constraint where conname = 'uk_old_designation_name'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from doc_sequences where prefix = 'SETUP-NEWS-PUBLIC'", Integer.class)).isEqualTo(1);
        validate("legacy");
    }

    private static void dropChecks(JdbcTemplate jdbc, String table, String column) {
        jdbc.queryForList("select con.conname from pg_constraint con join pg_class rel on rel.oid = con.conrelid"
                        + " where rel.relname = ? and con.contype = 'c' and pg_get_constraintdef(con.oid) like ?", String.class, table, "%" + column + "%")
                .forEach(name -> jdbc.execute("alter table " + table + " drop constraint \"" + name + "\""));
    }

    /** The same settings as application.properties. */
    private static Flyway flyway(DataSource db) {
        return Flyway.configure().dataSource(db).locations("classpath:db/migration")
                .baselineOnMigrate(true).baselineVersion("1").load();
    }

    private static DataSource freshDatabase(String name) {
        new JdbcTemplate(pg.getPostgresDatabase()).execute("create database " + name);
        return pg.getDatabase("postgres", name);
    }

    private static void validate(String name) {
        Map<String, Object> s = new HashMap<>();
        s.put(AvailableSettings.JAKARTA_JDBC_URL, pg.getJdbcUrl("postgres", name));
        s.put(AvailableSettings.JAKARTA_JDBC_USER, "postgres");
        s.put(AvailableSettings.JAKARTA_JDBC_PASSWORD, "");
        s.put(AvailableSettings.HBM2DDL_AUTO, "validate");
        SchemaTool.build(s);
    }
}
