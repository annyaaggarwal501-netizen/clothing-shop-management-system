package com.vertex.csms.common;

import java.time.LocalDate;

/** Indian financial year (1 April - 31 March). 26 Oct 2026 -> "2627". */
public final class FinancialYear {

    private FinancialYear() {}

    public static String code(LocalDate date) {
        int start = date.getMonthValue() >= 4 ? date.getYear() : date.getYear() - 1;
        return String.format("%02d%02d", start % 100, (start + 1) % 100);
    }
}
