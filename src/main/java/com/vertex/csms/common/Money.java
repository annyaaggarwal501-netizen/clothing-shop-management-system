package com.vertex.csms.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Money helpers. All money is BigDecimal with 2 decimals, ROUND_HALF_UP (REQ-INT-5). */
public final class Money {

    public static final BigDecimal ZERO = new BigDecimal("0.00");
    public static final BigDecimal HUNDRED = new BigDecimal("100");

    private Money() {}

    public static BigDecimal of(String value) {
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal round(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal nz(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    /** percent of amount, e.g. percent(3200, 15) = 480.00 */
    public static BigDecimal percent(BigDecimal amount, BigDecimal pct) {
        return round(amount.multiply(pct).divide(HUNDRED, 6, RoundingMode.HALF_UP));
    }
}
