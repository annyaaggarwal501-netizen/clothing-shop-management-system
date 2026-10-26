package com.vertex.csms.common;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Business rules kept in configuration rather than code (REQ-MNT-4). */
@ConfigurationProperties(prefix = "csms.rules")
public record RulesProperties(BigDecimal staffDiscountLimitPercent, int returnWindowDays,
                              int creditNoteValidityDays, int pointsPer100) {}
