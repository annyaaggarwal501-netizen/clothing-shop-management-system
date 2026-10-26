package com.vertex.csms.billing;

import java.math.BigDecimal;

import jakarta.persistence.*;

/** One bill line. unit_price is the tax-inclusive price; taxable_value and tax_amount are line totals. */
@Entity
@Table(name = "sale_line")
public class SaleLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "line_id")
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id")
    private Sale sale;

    private Long variantId;
    private Long offerId;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal unitDiscount;
    private BigDecimal gstRate;
    private BigDecimal taxableValue;
    private BigDecimal taxAmount;
    private int returnedQty;

    protected SaleLine() {}

    public SaleLine(Sale sale, Long variantId, Long offerId, int quantity, BigDecimal unitPrice,
                    BigDecimal unitDiscount, BigDecimal gstRate, BigDecimal taxableValue, BigDecimal taxAmount) {
        this.sale = sale;
        this.variantId = variantId;
        this.offerId = offerId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.unitDiscount = unitDiscount;
        this.gstRate = gstRate;
        this.taxableValue = taxableValue;
        this.taxAmount = taxAmount;
    }

    public Long getId() { return id; }
    public Sale getSale() { return sale; }
    public Long getVariantId() { return variantId; }
    public Long getOfferId() { return offerId; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getUnitDiscount() { return unitDiscount; }
    public BigDecimal getGstRate() { return gstRate; }
    public BigDecimal getTaxableValue() { return taxableValue; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public int getReturnedQty() { return returnedQty; }
    public void setReturnedQty(int returnedQty) { this.returnedQty = returnedQty; }
}
