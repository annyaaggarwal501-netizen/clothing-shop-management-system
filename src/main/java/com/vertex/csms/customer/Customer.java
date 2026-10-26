package com.vertex.csms.customer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.*;

@Entity
@Table(name = "customer")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "customer_id")
    private Long id;

    private String name;
    private String mobile;
    private String area;
    private LocalDate birthday;
    private LocalDate anniversary;
    private boolean waOptIn;
    private LocalDateTime consentAt;

    @Enumerated(EnumType.STRING)
    private ConsentMethod consentMethod;

    private Long consentBy;
    private int loyaltyPoints;
    private BigDecimal creditLimit = BigDecimal.ZERO;
    private BigDecimal creditDue = BigDecimal.ZERO;
    private boolean isActive = true;

    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Record WhatsApp consent (REQ-CUS-2). Withdrawal keeps the timestamp of the change. */
    public void setConsent(boolean optIn, ConsentMethod method, Long byUserId) {
        this.waOptIn = optIn;
        this.consentAt = LocalDateTime.now();
        this.consentBy = byUserId;
        if (optIn) {
            this.consentMethod = method;
        }
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }
    public LocalDate getBirthday() { return birthday; }
    public void setBirthday(LocalDate birthday) { this.birthday = birthday; }
    public LocalDate getAnniversary() { return anniversary; }
    public void setAnniversary(LocalDate anniversary) { this.anniversary = anniversary; }
    public boolean isWaOptIn() { return waOptIn; }
    public LocalDateTime getConsentAt() { return consentAt; }
    public ConsentMethod getConsentMethod() { return consentMethod; }
    public int getLoyaltyPoints() { return loyaltyPoints; }
    public void setLoyaltyPoints(int loyaltyPoints) { this.loyaltyPoints = loyaltyPoints; }
    public BigDecimal getCreditLimit() { return creditLimit; }
    public void setCreditLimit(BigDecimal creditLimit) { this.creditLimit = creditLimit; }
    public BigDecimal getCreditDue() { return creditDue; }
    public void setCreditDue(BigDecimal creditDue) { this.creditDue = creditDue; }
    public boolean isActive() { return isActive; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
