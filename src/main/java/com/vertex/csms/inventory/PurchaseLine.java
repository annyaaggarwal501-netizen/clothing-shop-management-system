package com.vertex.csms.inventory;

import java.math.BigDecimal;

import jakarta.persistence.*;

@Entity
@Table(name = "purchase_line")
public class PurchaseLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pline_id")
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "purchase_id")
    private Purchase purchase;

    private Long variantId;
    private int quantity;
    private BigDecimal unitCost;

    protected PurchaseLine() {}

    public PurchaseLine(Purchase purchase, Long variantId, int quantity, BigDecimal unitCost) {
        this.purchase = purchase;
        this.variantId = variantId;
        this.quantity = quantity;
        this.unitCost = unitCost;
    }
}
