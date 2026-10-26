package com.vertex.csms.inventory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.vertex.csms.common.ApiException;
import com.vertex.csms.product.Variant;

/**
 * The only place that changes variant.stock_qty. Every change also writes a stock_movement,
 * so SUM(stock_movement.qty_change) always equals variant.stock_qty (REQ-INT-6).
 * The caller must already hold the row lock (VariantRepository.lockByIds) for sales and returns.
 */
@Service
public class StockService {

    private final StockMovementRepository movements;
    private final AlertService alertService;

    public StockService(StockMovementRepository movements, AlertService alertService) {
        this.movements = movements;
        this.alertService = alertService;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void change(Variant v, MovementType type, int qtyChange, String refType, Long refId,
                       String reason, Long userId) {
        if (qtyChange == 0) {
            return;
        }
        int newQty = v.getStockQty() + qtyChange;
        if (newQty < 0) {
            String code = type == MovementType.SALE ? "ERR-INV-001" : "ERR-INV-002";
            throw ApiException.conflict(code, "Not enough stock for " + v.getProduct().getName() + " "
                    + v.getSize() + "/" + v.getColour() + " (" + v.getSku() + "): only " + v.getStockQty()
                    + " left. Remove it or reduce the quantity.", v.getSku());
        }
        v.setStockQty(newQty);
        movements.save(new StockMovement(v.getId(), type, qtyChange, refType, refId, reason, userId));
        alertService.onStockChanged(v, userId);
    }
}
