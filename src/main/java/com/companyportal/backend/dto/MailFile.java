/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.dto;

/**
 * A file uploaded for staff mail, kept for a few hours on the server. image: a picture; inline: it can also be shown
 * inside the message (JPG, PNG, GIF).
 */
public record MailFile(String id, String name, long size, String contentType, boolean image, boolean inline) {
}
