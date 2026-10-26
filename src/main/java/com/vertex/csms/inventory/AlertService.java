package com.vertex.csms.inventory;

import java.util.List;

import org.springframework.stereotype.Service;

import com.vertex.csms.product.Variant;

/** Raises LOW_STOCK / OUT_OF_STOCK alerts without duplicates, and closes them after restocking (REQ-INV-6). */
@Service
public class AlertService {

    private final AlertLogRepository alerts;

    public AlertService(AlertLogRepository alerts) {
        this.alerts = alerts;
    }

    public void onStockChanged(Variant v, Long userId) {
        String label = v.getProduct().getName() + " " + v.getSize() + "/" + v.getColour() + " (" + v.getSku() + ")";
        if (v.getStockQty() == 0) {
            raise("OUT_OF_STOCK", "ERROR", v.getId(), "Out of stock: " + label);
        } else if (v.getStockQty() <= v.getReorderLevel()) {
            raise("LOW_STOCK", "WARN", v.getId(), "Low stock: " + label + " - only " + v.getStockQty() + " left");
        }
        // Back above the reorder level: close any open stock alerts for this variant.
        if (v.getStockQty() > v.getReorderLevel()) {
            alerts.findByEntityIdAndAlertTypeInAndResolvedAtIsNull(v.getId(), List.of("LOW_STOCK", "OUT_OF_STOCK"))
                  .forEach(a -> a.resolve(userId));
        } else if (v.getStockQty() > 0) {
            alerts.findByEntityIdAndAlertTypeInAndResolvedAtIsNull(v.getId(), List.of("OUT_OF_STOCK"))
                  .forEach(a -> a.resolve(userId));
        }
    }

    public void raise(String type, String severity, Long entityId, String message) {
        if (entityId != null && alerts.existsByAlertTypeAndEntityIdAndResolvedAtIsNull(type, entityId)) {
            return;
        }
        alerts.save(new AlertLog(type, severity, entityId, message));
    }
}
