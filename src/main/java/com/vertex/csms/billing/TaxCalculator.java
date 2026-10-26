package com.vertex.csms.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.vertex.csms.common.Money;

/**
 * GST for one bill line. Pure Java with no database access, so it is easy to unit-test
 * (TaxCalculatorTest runs every row of gst_test_cases.csv).
 *
 * Prices are tax-inclusive. For one piece:
 *   unit net      = selling price - unit discount                         (discount BEFORE tax, REQ-OFR-3)
 *   rate r        = lowest slab whose limit the piece fits, testing net / (1 + slab rate)   (REQ-BIL-3)
 *   unit taxable  = net / (1 + r), rounded HALF_UP to 2 decimals          (REQ-BIL-2)
 *   unit tax      = net - unit taxable
 *   line values   = unit values x quantity
 *   CGST          = tax / 2 rounded HALF_UP; SGST = tax - CGST, so CGST + SGST = tax exactly (REQ-BIL-5)
 */
public final class TaxCalculator {

    private static final BigDecimal ONE = BigDecimal.ONE;

    private TaxCalculator() {}

    /** One slab: taxable value from minTaxable up to maxTaxable (null = no limit) is taxed at rate %. */
    public record Slab(BigDecimal minTaxable, BigDecimal maxTaxable, BigDecimal rate) {}

    public record LineTax(BigDecimal rate, BigDecimal unitTaxable, BigDecimal unitTax,
                          BigDecimal taxableValue, BigDecimal taxAmount) {}

    public record Split(BigDecimal cgst, BigDecimal sgst) {}

    /**
     * Test the lowest slab first: if net / (1 + its rate) stays within its limit, use it; otherwise move up.
     * FABRIC has one open-ended 5% slab, so it is 5% at any price.
     */
    public static BigDecimal rateFor(List<Slab> slabs, BigDecimal unitNet) {
        if (slabs == null || slabs.isEmpty()) {
            throw new IllegalStateException("No GST slab is configured for this tax class");
        }
        for (Slab slab : slabs) {
            BigDecimal taxable = unitNet.divide(ONE.add(slab.rate().movePointLeft(2)), 10, RoundingMode.HALF_UP);
            if (slab.maxTaxable() == null || taxable.compareTo(slab.maxTaxable()) <= 0) {
                return slab.rate();
            }
        }
        return slabs.get(slabs.size() - 1).rate();
    }

    public static LineTax compute(List<Slab> slabs, BigDecimal unitNet, int quantity) {
        BigDecimal rate = rateFor(slabs, unitNet);
        BigDecimal unitTaxable = unitNet.divide(ONE.add(rate.movePointLeft(2)), 2, RoundingMode.HALF_UP);
        BigDecimal unitTax = unitNet.subtract(unitTaxable);
        BigDecimal qty = BigDecimal.valueOf(quantity);
        return new LineTax(rate, unitTaxable, Money.round(unitTax),
                Money.round(unitTaxable.multiply(qty)), Money.round(unitTax.multiply(qty)));
    }

    public static Split split(BigDecimal tax) {
        BigDecimal cgst = tax.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        return new Split(cgst, Money.round(tax.subtract(cgst)));
    }

    /**
     * WARN-GST-001 (REQ-BIL-4): the price band where a small discount changes the slab.
     * With slabs 5% up to 2500 and 18% above, that is net price 2625.01 .. 2950.00.
     */
    public static boolean isSlabSensitive(List<Slab> slabs, BigDecimal unitNet) {
        if (slabs.size() < 2 || slabs.get(0).maxTaxable() == null) {
            return false;
        }
        BigDecimal limit = slabs.get(0).maxTaxable();
        BigDecimal low = Money.round(limit.multiply(ONE.add(slabs.get(0).rate().movePointLeft(2))));
        BigDecimal high = Money.round(limit.multiply(ONE.add(slabs.get(1).rate().movePointLeft(2))));
        return unitNet.compareTo(low) > 0 && unitNet.compareTo(high) <= 0;
    }
}
