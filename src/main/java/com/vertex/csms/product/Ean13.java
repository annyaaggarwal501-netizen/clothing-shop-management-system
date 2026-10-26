package com.vertex.csms.product;

/** EAN-13 helpers. In-store barcodes use the GS1 "200" prefix so they never clash with real products. */
public final class Ean13 {

    private Ean13() {}

    public static int checkDigit(String first12) {
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int d = first12.charAt(i) - '0';
            sum += (i % 2 == 0) ? d : d * 3;
        }
        return (10 - sum % 10) % 10;
    }

    /** The barcode after the given one, e.g. 2000000002743 -> 2000000002750. */
    public static String next(String current) {
        long base = current == null ? 200000000000L : Long.parseLong(current.substring(0, 12));
        String next12 = String.valueOf(base + 1);
        return next12 + checkDigit(next12);
    }
}
