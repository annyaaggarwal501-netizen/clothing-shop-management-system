package com.vertex.csms.returns;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.*;

/** Value given back for returned items; redeemable as a payment within 90 days (REQ-RET-4/5). */
@Entity
@Table(name = "credit_note")
public class CreditNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cn_id")
    private Long id;

    private String cnNo;
    private Long saleId;
    private BigDecimal amount;
    private BigDecimal balance;
    private LocalDate validTo;
    private Long createdBy;
    private String reason;

    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected CreditNote() {}

    public CreditNote(String cnNo, Long saleId, BigDecimal amount, LocalDate validTo, Long createdBy, String reason) {
        this.cnNo = cnNo;
        this.saleId = saleId;
        this.amount = amount;
        this.balance = amount;
        this.validTo = validTo;
        this.createdBy = createdBy;
        this.reason = reason;
    }

    public Long getId() { return id; }
    public String getCnNo() { return cnNo; }
    public Long getSaleId() { return saleId; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public LocalDate getValidTo() { return validTo; }
    public String getReason() { return reason; }
}
