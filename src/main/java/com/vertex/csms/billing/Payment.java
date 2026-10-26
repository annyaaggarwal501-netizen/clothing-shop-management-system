package com.vertex.csms.billing;

import java.math.BigDecimal;

import jakarta.persistence.*;

@Entity
@Table(name = "payment")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id")
    private Sale sale;

    @Enumerated(EnumType.STRING)
    private PaymentMode mode;

    private BigDecimal amount;
    private String reference;

    protected Payment() {}

    public PaymentMode getMode() { return mode; }
    public BigDecimal getAmount() { return amount; }
    public String getReference() { return reference; }

    public Payment(Sale sale, PaymentMode mode, BigDecimal amount, String reference) {
        this.sale = sale;
        this.mode = mode;
        this.amount = amount;
        this.reference = reference;
    }
}
