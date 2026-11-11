/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.dto;

import java.time.LocalDate;

/**
 * Today's date at the office (Asia/Kolkata) and my day so far, or null before my first check-in. photoCapture false =
 * I check in without a photo (Check in without photo): only the time and GPS location.
 */
public record AttendanceToday(LocalDate date, String zone, AttendanceDaySummary day, boolean photoCapture) {
}
