/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.dto;

import com.companyportal.backend.entity.TradeParty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** One personal mail to one customer or supplier (Product portal mail); the content is in MailContent. */
@Getter
@Setter
public class PartyMailRequest extends MailContent {

    @NotNull
    private TradeParty party;

    @NotNull
    private Long partyId;
}
