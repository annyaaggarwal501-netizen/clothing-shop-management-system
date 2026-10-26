package com.vertex.csms.inventory;

import jakarta.persistence.*;

/** One line in the stock ledger. Never edited or deleted (REQ-INV-1). */
@Entity
@Table(name = "stock_movement")
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "movement_id")
    private Long id;

    private Long variantId;

    @Enumerated(EnumType.STRING)
    private MovementType type;

    private int qtyChange;
    private String refType;
    private Long refId;
    private String reason;
    private Long userId;

    protected StockMovement() {}

    public StockMovement(Long variantId, MovementType type, int qtyChange, String refType, Long refId,
                         String reason, Long userId) {
        this.variantId = variantId;
        this.type = type;
        this.qtyChange = qtyChange;
        this.refType = refType;
        this.refId = refId;
        this.reason = reason;
        this.userId = userId;
    }

    public Long getId() { return id; }
}
