package com.vertex.csms.billing;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

/**
 * Runs every row of gst_test_cases.csv (the 11 billing test cases from the data pack) through the
 * TaxCalculator. The slabs below are the same rows V2__seed_data.sql puts in the gst_slab table.
 */
class TaxCalculatorTest {

    static final List<TaxCalculator.Slab> APPAREL = List.of(
            new TaxCalculator.Slab(new BigDecimal("0.00"), new BigDecimal("2500.00"), new BigDecimal("5.00")),
            new TaxCalculator.Slab(new BigDecimal("2500.01"), null, new BigDecimal("18.00")));

    static final List<TaxCalculator.Slab> FABRIC = List.of(
            new TaxCalculator.Slab(new BigDecimal("0.00"), null, new BigDecimal("5.00")));

    @ParameterizedTest(name = "{0} {1}: Rs {3} - {4} -> {6}%")
    @CsvFileSource(resources = "/gst_test_cases.csv", numLinesToSkip = 1, encoding = "UTF-8")
    void gstTestCase(String testId, String item, String taxClass, BigDecimal tagPrice, BigDecimal unitDiscount,
                     BigDecimal netPrice, BigDecimal expectedRate, BigDecimal expectedTaxable,
                     BigDecimal expectedCgst, BigDecimal expectedSgst, String why) {
        List<TaxCalculator.Slab> slabs = taxClass.equals("FABRIC") ? FABRIC : APPAREL;
        BigDecimal net = tagPrice.subtract(unitDiscount);
        assertEquals(0, net.compareTo(netPrice), testId + " net price");

        TaxCalculator.LineTax tax = TaxCalculator.compute(slabs, net, 1);
        TaxCalculator.Split split = TaxCalculator.split(tax.taxAmount());

        assertEquals(0, tax.rate().compareTo(expectedRate), testId + " rate");
        assertEquals(0, tax.taxableValue().compareTo(expectedTaxable), testId + " taxable");
        assertEquals(0, split.cgst().compareTo(expectedCgst), testId + " CGST");
        assertEquals(0, split.sgst().compareTo(expectedSgst), testId + " SGST");
        // CGST + SGST + taxable must add back to the price charged, to the paisa.
        assertEquals(0, tax.taxableValue().add(split.cgst()).add(split.sgst()).compareTo(net), testId + " adds up");

        if (why.contains("WARN-GST-001")) {
            assertTrue(TaxCalculator.isSlabSensitive(slabs, net), testId + " should raise WARN-GST-001");
        }
    }

    @Test
    void slabSensitiveBandIs2625_01To2950() {
        assertFalse(TaxCalculator.isSlabSensitive(APPAREL, new BigDecimal("2625.00")));
        assertTrue(TaxCalculator.isSlabSensitive(APPAREL, new BigDecimal("2625.01")));
        assertTrue(TaxCalculator.isSlabSensitive(APPAREL, new BigDecimal("2950.00")));
        assertFalse(TaxCalculator.isSlabSensitive(APPAREL, new BigDecimal("2950.01")));
        assertFalse(TaxCalculator.isSlabSensitive(FABRIC, new BigDecimal("2800.00")));
    }

    @Test
    void quantityMultipliesUnitValues() {
        // 3 kurtis at Rs 1,050: unit taxable 1000.00, unit tax 50.00
        TaxCalculator.LineTax tax = TaxCalculator.compute(APPAREL, new BigDecimal("1050.00"), 3);
        assertEquals(new BigDecimal("3000.00"), tax.taxableValue());
        assertEquals(new BigDecimal("150.00"), tax.taxAmount());
    }

    @Test
    void festiveSareeOfferStaysAtFivePercent() {
        // Demo: Rs 3,200 silk saree with 15% festive offer -> Rs 2,720, FABRIC -> 5%
        TaxCalculator.LineTax tax = TaxCalculator.compute(FABRIC, new BigDecimal("2720.00"), 1);
        assertEquals(0, tax.rate().compareTo(new BigDecimal("5")));
        assertEquals(new BigDecimal("2590.48"), tax.taxableValue());
    }
}
