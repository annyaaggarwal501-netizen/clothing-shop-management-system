package com.vertex.csms.inventory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vertex.csms.common.ApiException;
import com.vertex.csms.product.Variant;
import com.vertex.csms.product.VariantRepository;
import com.vertex.csms.security.CurrentUser;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

@Service
public class PurchaseService {

    private final PurchaseRepository purchases;
    private final SupplierRepository suppliers;
    private final VariantRepository variants;
    private final StockService stock;

    public PurchaseService(PurchaseRepository purchases, SupplierRepository suppliers,
                           VariantRepository variants, StockService stock) {
        this.purchases = purchases;
        this.suppliers = suppliers;
        this.variants = variants;
        this.stock = stock;
    }

    public record PurchaseRequest(
            @NotNull(message = "Choose a supplier") Long supplierId,
            @NotBlank(message = "Enter the supplier bill number") @Size(max = 30) String billNo,
            @NotNull @PastOrPresent LocalDate billDate,
            @NotEmpty(message = "Add at least one item") List<@Valid Line> lines) {

        public record Line(@NotNull Long variantId,
                           @NotNull @Min(value = 1, message = "Quantity must be at least 1") Integer quantity,
                           @NotNull @DecimalMin("0.00") BigDecimal unitCost) {}
    }

    public record PurchaseResult(Long purchaseId, int lines, int totalPieces) {}

    /** Saves the purchase, adds stock and updates weighted-average cost in one transaction (REQ-INV-2). */
    @Transactional
    public PurchaseResult record(PurchaseRequest req) {
        suppliers.findById(req.supplierId())
                 .orElseThrow(() -> ApiException.badRequest("ERR-VAL-001", "Supplier not found", "supplierId"));
        Long userId = CurrentUser.id();

        Purchase purchase = purchases.save(new Purchase(req.supplierId(), req.billNo().trim(), req.billDate(), userId));

        List<Long> ids = req.lines().stream().map(PurchaseRequest.Line::variantId).distinct().toList();
        Map<Long, Variant> locked = variants.lockByIds(ids).stream()
                .collect(Collectors.toMap(Variant::getId, Function.identity()));

        int pieces = 0;
        for (PurchaseRequest.Line line : req.lines()) {
            Variant v = locked.get(line.variantId());
            if (v == null) {
                throw ApiException.badRequest("ERR-PRD-004", "Item " + line.variantId() + " not found", "variantId");
            }
            // Weighted-average cost = (old stock x old cost + new qty x new cost) / total qty
            int oldQty = Math.max(v.getStockQty(), 0);
            BigDecimal totalValue = v.getCostPrice().multiply(BigDecimal.valueOf(oldQty))
                    .add(line.unitCost().multiply(BigDecimal.valueOf(line.quantity())));
            v.setCostPrice(totalValue.divide(BigDecimal.valueOf(oldQty + line.quantity()), 2, RoundingMode.HALF_UP));

            purchase.getLines().add(new PurchaseLine(purchase, v.getId(), line.quantity(), line.unitCost()));
            stock.change(v, MovementType.PURCHASE, line.quantity(), "PURCHASE", purchase.getId(),
                    "Bill " + req.billNo().trim(), userId);
            pieces += line.quantity();
        }
        return new PurchaseResult(purchase.getId(), req.lines().size(), pieces);
    }
}
