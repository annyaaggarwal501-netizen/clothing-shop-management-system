package com.vertex.csms.product;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

/** Body of POST/PUT /api/v1/products — the product form plus its size x colour grid. */
public record ProductRequest(
        @NotNull(message = "Choose a category") Long categoryId,
        @NotBlank @Size(min = 2, max = 100) String name,
        @Size(max = 50) String brand,
        @Size(max = 50) String fabric,
        @NotNull Gender gender,
        @NotBlank @Pattern(regexp = "^[0-9]{4,8}$", message = "HSN code must be 4 to 8 digits") String hsnCode,
        @NotNull TaxClass taxClass,
        boolean returnable,
        Boolean active,
        String clearanceReason,
        @NotEmpty(message = "Add at least one size/colour") List<@Valid VariantRow> variants) {

    public record VariantRow(
            Long id,                                   // null = new variant
            @NotBlank @Size(max = 10) String size,
            @NotBlank @Size(max = 30) String colour,
            @NotNull @DecimalMin("0.00") BigDecimal costPrice,
            @NotNull @DecimalMin(value = "0.01", message = "Selling price must be more than 0") BigDecimal sellingPrice,
            @NotNull BigDecimal mrp,
            @Min(0) @Max(99999) Integer openingStock,  // only used for new variants
            @Min(0) Integer reorderLevel,
            @Pattern(regexp = "^$|^[0-9]{8,20}$", message = "Barcode must be digits") String barcode) {}
}
