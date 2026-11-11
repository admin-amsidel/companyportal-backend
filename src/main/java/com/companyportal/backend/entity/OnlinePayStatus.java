/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.entity;

/**
 * An online payment: CREATED when the Razorpay order is made (the customer is paying), PAID once Razorpay confirms the
 * money, FAILED when the bank or card refused it, CANCELLED when the payment window was closed without paying.
 */
public enum OnlinePayStatus {
    CREATED,
    PAID,
    FAILED,
    CANCELLED
}
