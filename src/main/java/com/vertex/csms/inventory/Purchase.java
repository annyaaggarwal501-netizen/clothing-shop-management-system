package com.vertex.csms.inventory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;

/** A stock-in entry: one supplier bill with its lines (REQ-INV-2). */
@Entity
@Table(name = "purchase")
public class Purchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "purchase_id")
    private Long id;

    private Long supplierId;
    private String billNo;
    private LocalDate billDate;
    private Long createdBy;

    @OneToMany(mappedBy = "purchase", cascade = CascadeType.ALL)
    private List<PurchaseLine> lines = new ArrayList<>();

    protected Purchase() {}

    public Purchase(Long supplierId, String billNo, LocalDate billDate, Long createdBy) {
        this.supplierId = supplierId;
        this.billNo = billNo;
        this.billDate = billDate;
        this.createdBy = createdBy;
    }

    public Long getId() { return id; }
    public List<PurchaseLine> getLines() { return lines; }
}
