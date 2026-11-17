/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.dto;

import com.companyportal.backend.entity.PayComponentKind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** What the payroll screens send. */
public final class PayrollRequests {

    private PayrollRequests() {
    }

    @Data
    public static class Component {
        @NotBlank
        @Size(max = 20)
        @Pattern(regexp = "[A-Za-z0-9_]+", message = "Letters, digits and _ only, e.g. HRA")
        private String code;

        @NotBlank
        @Size(max = 60)
        private String name;

        @NotNull
        private PayComponentKind kind;

        @Min(0)
        @Max(999)
        private int sortOrder;

        private boolean active = true;

        /** Counted as tax paid (Income Tax, Professional Tax). */
        private boolean tax;
    }

    /**
     * One salary revision of a financial year: the monthly amount of each component (missing = 0). revisionId: the one to
     * change; empty = a new revision (the year's first, or a raise from effectiveFrom). EPF, ESI and professional tax are
     * worked out when they apply; the year's tax set-up (regime, declaration, income and tax paid outside the portal)
     * is shared by its revisions.
     */
    @Data
    public static class Salary {
        private Long revisionId;

        @NotNull
        private LocalDate effectiveFrom;

        /** Empty = keep (a new revision: as the one before it). */
        private Boolean pfApplies;
        private Boolean pfFullWage;
        private Boolean esiApplies;
        private Boolean ptApplies;

        /** Gross salary paid this year outside the portal (before it was used, or by a previous employer); empty = keep. */
        @DecimalMin(value = "0", message = "Income cannot be negative")
        @Digits(integer = 12, fraction = 2, message = "At most 2 decimals")
        private BigDecimal outsideIncome;

        /** Income tax already deducted this year outside the portal; empty = keep. */
        @DecimalMin(value = "0", message = "Tax cannot be negative")
        @Digits(integer = 12, fraction = 2, message = "At most 2 decimals")
        private BigDecimal outsideTax;

        @Size(max = 500)
        private String notes;

        /** Working days in a month (per-day pay = monthly earnings / working days); empty = 30. */
        @Min(value = 1, message = "Working days: 1 to 31")
        @Max(value = 31, message = "Working days: 1 to 31")
        private Integer workingDays;

        @NotNull
        @Size(max = 50)
        private List<@Valid Line> lines;

        /** The person's PAN and salary account for payslips; optional, kept on the employee. */
        @Pattern(regexp = "|[A-Za-z]{5}[0-9]{4}[A-Za-z]", message = "PAN is 5 letters, 4 digits and a letter, e.g. ABCDE1234F")
        private String pan;

        @Pattern(regexp = "|[0-9]{6,20}", message = "Bank account number: 6 to 20 digits")
        private String bankAccount;

        @Pattern(regexp = "|[A-Za-z]{4}0[A-Za-z0-9]{6}", message = "IFSC is 11 characters: 4 letters, 0, then 6 letters or digits, e.g. HDFC0001234")
        private String ifsc;

        @Pattern(regexp = "|[A-Za-z0-9._-]{2,256}@[A-Za-z]{2,64}", message = "UPI ID looks like name@bank, e.g. anu@okhdfcbank")
        private String upi;

        /** NEW / OLD: the income tax is worked out; MANUAL: typed in (needs the Manual income tax access). Empty = keep (new salary: NEW). */
        private com.companyportal.backend.entity.TaxRegime taxRegime;

        /** Old regime: deductions and exemptions for the year (80C, 80D, HRA, home-loan interest...). */
        @DecimalMin(value = "0", message = "Deductions cannot be negative")
        @Digits(integer = 12, fraction = 2, message = "At most 2 decimals")
        private BigDecimal taxDeclared;
    }

    /**
     * A salary revision being typed, for the year's income tax under both regimes before it is saved: it stands in for
     * revisionId (empty = added as a new revision) among the person's revisions of the year.
     */
    @Data
    public static class TaxPreview {
        @NotNull
        private Long employeeId;

        private Long revisionId;

        @NotNull
        private LocalDate effectiveFrom;

        @Min(1)
        @Max(31)
        private Integer workingDays;

        @NotNull
        @Size(max = 50)
        private List<@Valid Line> lines;

        private boolean pfApplies;
        private boolean pfFullWage;
        private boolean esiApplies;
        private boolean ptApplies;

        @DecimalMin("0")
        @Digits(integer = 12, fraction = 2)
        private BigDecimal declared;

        @DecimalMin("0")
        @Digits(integer = 12, fraction = 2)
        private BigDecimal outsideIncome;

        @DecimalMin("0")
        @Digits(integer = 12, fraction = 2)
        private BigDecimal outsideTax;
    }

    /** The professional tax slabs, all of them, lowest first. */
    @Data
    public static class PtSlabs {
        @NotNull
        @Size(min = 1, max = 30, message = "Give 1 to 30 slabs")
        private List<@Valid PtSlab> slabs;
    }

    @Data
    public static class PtSlab {
        @NotNull
        @DecimalMin(value = "0", message = "Income cannot be negative")
        @Digits(integer = 12, fraction = 2)
        private BigDecimal fromIncome;

        @NotNull
        @DecimalMin(value = "0", message = "Tax cannot be negative")
        @Digits(integer = 8, fraction = 2)
        private BigDecimal halfYearTax;
    }

    @Data
    public static class Line {
        @NotNull
        private Long componentId;

        @NotNull
        @DecimalMin(value = "0", message = "Amounts cannot be negative")
        @Digits(integer = 10, fraction = 2, message = "At most 2 decimals")
        private BigDecimal monthly;
    }
}
