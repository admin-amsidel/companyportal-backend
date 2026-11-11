/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.config;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

/**
 * How the mail connection is secured follows the port, so only MAIL_PORT changes between networks: 465 opens an SSL
 * connection straight away; any other port (2525, 587) starts plain and switches to TLS (STARTTLS). Render's free plan
 * lets only 2525 out; some office networks let only 465 out.
 */
@Configuration
public class MailPortConfig {

    public static final int SSL_PORT = 465;

    /** Sets SSL or STARTTLS on these JavaMail properties for this port. */
    public static void secure(Properties p, int port) {
        boolean ssl = port == SSL_PORT;
        p.put("mail.smtp.ssl.enable", String.valueOf(ssl));
        p.put("mail.smtp.starttls.enable", String.valueOf(!ssl));
        p.put("mail.smtp.starttls.required", String.valueOf(!ssl));
    }

    /** The default account's sender, built by Spring Boot from spring.mail.*. */
    @Bean
    static BeanPostProcessor mailPortSecurity() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof JavaMailSenderImpl s) {
                    secure(s.getJavaMailProperties(), s.getPort());
                }
                return bean;
            }
        };
    }
}
