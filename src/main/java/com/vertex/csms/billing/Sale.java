package com.vertex.csms.billing;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;

@Entity
@Table(name = "sale")
public class Sale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sale_id")
    private Long id;

    private String invoiceNo;
    private Long customerId;
    private Long createdBy;

    @Enumerated(EnumType.STRING)
    private SaleStatus status = SaleStatus.DRAFT;

    private String docType = "TAX_INVOICE";
    private BigDecimal subtotal;
    private BigDecimal discountTotal;
    private BigDecimal taxableTotal;
    private BigDecimal cgstTotal;
    private BigDecimal sgstTotal;
    private BigDecimal roundOff;
    private BigDecimal grandTotal;

    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;
    private String voidReason;
    private Long voidedBy;
    private LocalDateTime voidedAt;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL)
    @OrderBy("id")
    private List<SaleLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL)
    private List<Payment> payments = new ArrayList<>();

    public Long getId() { return id; }
    public String getInvoiceNo() { return invoiceNo; }
    public void setInvoiceNo(String invoiceNo) { this.invoiceNo = invoiceNo; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public SaleStatus getStatus() { return status; }
    public void setStatus(SaleStatus status) { this.status = status; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    public BigDecimal getDiscountTotal() { return discountTotal; }
    public void setDiscountTotal(BigDecimal discountTotal) { this.discountTotal = discountTotal; }
    public BigDecimal getTaxableTotal() { return taxableTotal; }
    public void setTaxableTotal(BigDecimal taxableTotal) { this.taxableTotal = taxableTotal; }
    public BigDecimal getCgstTotal() { return cgstTotal; }
    public void setCgstTotal(BigDecimal cgstTotal) { this.cgstTotal = cgstTotal; }
    public BigDecimal getSgstTotal() { return sgstTotal; }
    public void setSgstTotal(BigDecimal sgstTotal) { this.sgstTotal = sgstTotal; }
    public BigDecimal getRoundOff() { return roundOff; }
    public void setRoundOff(BigDecimal roundOff) { this.roundOff = roundOff; }
    public BigDecimal getGrandTotal() { return grandTotal; }
    public void setGrandTotal(BigDecimal grandTotal) { this.grandTotal = grandTotal; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void markVoided(String reason, Long userId) {
        this.status = SaleStatus.CANCELLED;
        this.voidReason = reason;
        this.voidedBy = userId;
        this.voidedAt = LocalDateTime.now();
    }
    public List<SaleLine> getLines() { return lines; }
    public List<Payment> getPayments() { return payments; }
}
