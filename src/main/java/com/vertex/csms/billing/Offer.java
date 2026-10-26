package com.vertex.csms.billing;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.*;

@Entity
@Table(name = "offer")
public class Offer {

    public enum Type { PERCENT, FLAT, BUY_X_GET_Y, BILL_VALUE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "offer_id")
    private Long id;

    private String name;

    @Enumerated(EnumType.STRING)
    private Type type;

    private BigDecimal value;
    private Long categoryId;
    private Long productId;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private boolean isActive;

    public Long getId() { return id; }
    public String getName() { return name; }
    public Type getType() { return type; }
    public BigDecimal getValue() { return value; }
    public Long getCategoryId() { return categoryId; }
    public Long getProductId() { return productId; }
    public LocalDateTime getValidFrom() { return validFrom; }
    public LocalDateTime getValidTo() { return validTo; }
    public boolean isActive() { return isActive; }
}
