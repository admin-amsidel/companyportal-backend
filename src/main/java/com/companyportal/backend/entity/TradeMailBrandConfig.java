/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;

/** One brand's customer &amp; supplier email limits (Customer portal, Email > config). */
@Entity
@Table(name = "trade_mail_brand_config")
@NoArgsConstructor
public class TradeMailBrandConfig extends MailLimits {
}
