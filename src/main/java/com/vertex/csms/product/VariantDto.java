package com.vertex.csms.product;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Variant as the API returns it. costPrice is null (and left out of the JSON) for Sales Staff:
 * cost prices are hidden from Staff in every API response (SRS 3.2).
 */
public record VariantDto(
        Long id, Long productId, String productName, String brand, String sku, String barcode,
        String size, String colour,
        @JsonInclude(JsonInclude.Include.NON_NULL) BigDecimal costPrice,
        BigDecimal sellingPrice, BigDecimal mrp, int stockQty, int reorderLevel, String stockStatus,
        TaxClass taxClass, String hsnCode, boolean returnable, String categoryName) {

    public static VariantDto of(Variant v, boolean showCost) {
        Product p = v.getProduct();
        return new VariantDto(v.getId(), p.getId(), p.getName(), p.getBrand(), v.getSku(), v.getBarcode(),
                v.getSize(), v.getColour(), showCost ? v.getCostPrice() : null,
                v.getSellingPrice(), v.getMrp(), v.getStockQty(), v.getReorderLevel(), v.stockStatus(),
                p.getTaxClass(), p.getHsnCode(), p.isReturnable(), p.getCategory().getName());
    }
}
