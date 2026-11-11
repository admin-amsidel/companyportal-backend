/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.tools;

import jakarta.persistence.Converter;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.engine.config.spi.ConfigurationService;
import org.hibernate.tool.schema.spi.SchemaManagementToolCoordinator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.hibernate.SpringImplicitNamingStrategy;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

/**
 * Schema tools for Flyway work; neither runs in a normal build.
 * <ul>
 *   <li><b>Export</b> the PostgreSQL schema the entities describe, with no database (e.g. to compare with the
 *   migrations when writing a new one): {@code ./mvnw test -Dtest=SchemaTool -Dschema.export=target/schema.sql}</li>
 *   <li><b>Check</b> a database against the entities, read only (what ddl-auto=validate does at start-up), before a
 *   deploy: {@code ./mvnw test -Dtest=SchemaTool -Dschema.validate.url=jdbc:postgresql://... -Dschema.validate.user=...
 *   -Dschema.validate.password=...}</li>
 * </ul>
 * Uses the same naming rules as Spring Boot, so table and column names match the running app's.
 */
class SchemaTool {

    private static final String BASE_PACKAGE = "com.companyportal.backend";

    @Test
    @EnabledIfSystemProperty(named = "schema.export", matches = ".+")
    void export() throws Exception {
        Path out = Path.of(System.getProperty("schema.export")).toAbsolutePath();
        Files.createDirectories(out.getParent());
        Files.deleteIfExists(out);
        Map<String, Object> s = new HashMap<>();
        s.put(AvailableSettings.DIALECT, PostgreSQLDialect.class.getName());
        s.put("hibernate.boot.allow_jdbc_metadata_access", "false");
        // Supabase runs PostgreSQL 15 or later
        s.put(AvailableSettings.JAKARTA_HBM2DDL_DB_MAJOR_VERSION, "15");
        s.put(AvailableSettings.JAKARTA_HBM2DDL_DATABASE_ACTION, "none");
        s.put(AvailableSettings.JAKARTA_HBM2DDL_SCRIPTS_ACTION, "create");
        s.put(AvailableSettings.JAKARTA_HBM2DDL_SCRIPTS_CREATE_TARGET, out.toString());
        s.put(AvailableSettings.HBM2DDL_DELIMITER, ";");
        s.put(AvailableSettings.FORMAT_SQL, "true");
        build(s);
        System.out.println("Schema written to " + out);
    }

    @Test
    @EnabledIfSystemProperty(named = "schema.validate.url", matches = ".+")
    void validate() {
        Map<String, Object> s = new HashMap<>();
        s.put(AvailableSettings.JAKARTA_JDBC_URL, System.getProperty("schema.validate.url"));
        s.put(AvailableSettings.JAKARTA_JDBC_USER, System.getProperty("schema.validate.user", ""));
        s.put(AvailableSettings.JAKARTA_JDBC_PASSWORD, System.getProperty("schema.validate.password", ""));
        s.put(AvailableSettings.HBM2DDL_AUTO, "validate");
        build(s);
        System.out.println("The database matches the entities");
    }

    /** Runs the schema action in the settings over every entity. */
    static void build(Map<String, Object> settings) {
        StandardServiceRegistryBuilder b = new StandardServiceRegistryBuilder()
                .applySetting(AvailableSettings.PHYSICAL_NAMING_STRATEGY, PhysicalNamingStrategySnakeCaseImpl.class.getName())
                .applySetting(AvailableSettings.IMPLICIT_NAMING_STRATEGY, SpringImplicitNamingStrategy.class.getName());
        settings.forEach(b::applySetting);
        StandardServiceRegistry registry = b.build();
        try {
            MetadataSources sources = new MetadataSources(registry);
            ClassPathScanningCandidateComponentProvider scan = new ClassPathScanningCandidateComponentProvider(false);
            scan.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
            scan.addIncludeFilter(new AnnotationTypeFilter(Embeddable.class));
            scan.addIncludeFilter(new AnnotationTypeFilter(MappedSuperclass.class));
            scan.addIncludeFilter(new AnnotationTypeFilter(Converter.class));
            TreeSet<String> names = new TreeSet<>();
            for (BeanDefinition d : scan.findCandidateComponents(BASE_PACKAGE)) {
                names.add(d.getBeanClassName());
            }
            for (String name : names) {
                sources.addAnnotatedClass(Class.forName(name));
            }
            // only the schema step, not a whole session factory (entity listeners are Spring beans)
            Map<String, Object> config = new HashMap<>(registry.requireService(ConfigurationService.class).getSettings());
            SchemaManagementToolCoordinator.process(sources.buildMetadata(), registry, config, null);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
