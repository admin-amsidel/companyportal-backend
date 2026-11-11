/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PinValidator implements ConstraintValidator<Pin, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || PinPolicy.isValid(value);
    }
}
