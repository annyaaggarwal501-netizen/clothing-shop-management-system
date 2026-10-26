package com.vertex.csms.billing;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.vertex.csms.product.TaxClass;

import jakarta.persistence.*;

@Entity
@Table(name = "gst_slab")
public class GstSlab {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "slab_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    private TaxClass taxClass;

    private BigDecimal minTaxable;
    private BigDecimal maxTaxable;
    private BigDecimal rate;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;

    public TaxClass getTaxClass() { return taxClass; }
    public BigDecimal getMinTaxable() { return minTaxable; }
    public BigDecimal getMaxTaxable() { return maxTaxable; }
    public BigDecimal getRate() { return rate; }
}
