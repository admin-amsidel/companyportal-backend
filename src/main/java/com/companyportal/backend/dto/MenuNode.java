/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.dto;

import java.util.List;

/** A menu with its sub-menus, in display order; ownTick: a tick on its parent does not give it. */
public record MenuNode(String code, String name, String description, int checkpoint, String path, String icon,
                       List<MenuNode> children, boolean ownTick) {
}
