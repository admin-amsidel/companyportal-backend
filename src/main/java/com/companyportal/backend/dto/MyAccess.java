/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.dto;

import java.util.List;

/** What the signed-in person may open: every menu (all), the menu codes, and the menu tree to show; their time zone, and their brand's colours (null = the standard look). */
public record MyAccess(boolean all, List<String> codes, List<MenuNode> menus, String timeZone, String palette) {
}
