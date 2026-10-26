package com.vertex.csms.billing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vertex.csms.common.ApiException;
import com.vertex.csms.security.CurrentUser;

/**
 * Hold Bill (F8): park a cart as a HELD sale so the counter can bill the next customer, then resume it.
 * A held bill has no invoice number, no payments and does not touch stock. Held bills older than
 * 24 hours are discarded (SRS 4.3.2).
 */
@Service
public class HeldBillService {

    private final BillingService billing;
    private final SaleRepository sales;
    private final JdbcTemplate jdbc;

    public HeldBillService(BillingService billing, SaleRepository sales, JdbcTemplate jdbc) {
        this.billing = billing;
        this.sales = sales;
        this.jdbc = jdbc;
    }

    public record HoldResult(Long heldId, int items, java.math.BigDecimal total) {}

    @Transactional
    public HoldResult hold(BillingService.CartRequest req) {
        if (req.lines().isEmpty()) {
            throw ApiException.badRequest("ERR-BIL-001", "The cart is empty, so there is nothing to hold.");
        }
        BillingService.Quote q = billing.quote(req);
        Sale s = new Sale();
        s.setStatus(SaleStatus.HELD);
        s.setCustomerId(req.customerId());
        s.setCreatedBy(CurrentUser.id());
        s.setSubtotal(q.subtotal());
        s.setDiscountTotal(q.discountTotal());
        s.setTaxableTotal(q.taxableTotal());
        s.setCgstTotal(q.cgstTotal());
        s.setSgstTotal(q.sgstTotal());
        s.setRoundOff(q.roundOff());
        s.setGrandTotal(q.grandTotal());
        for (BillingService.QuoteLine l : q.lines()) {
            s.getLines().add(new SaleLine(s, l.variantId(), l.offerId(), l.quantity(), l.unitPrice(),
                    l.unitDiscount(), l.gstRate(), l.taxableValue(), l.taxAmount()));
        }
        sales.save(s);
        return new HoldResult(s.getId(), q.totalQty(), q.grandTotal());
    }

    @Transactional
    public List<Map<String, Object>> list() {
        discardOld();
        return jdbc.queryForList("""
                SELECT s.sale_id AS held_id, s.created_at, s.grand_total, c.name AS customer_name, c.mobile,
                       u.full_name AS cashier,
                       (SELECT SUM(quantity) FROM sale_line sl WHERE sl.sale_id = s.sale_id) AS items,
                       (SELECT GROUP_CONCAT(p.name ORDER BY sl.line_id SEPARATOR ', ')
                          FROM sale_line sl JOIN variant v ON v.variant_id = sl.variant_id
                          JOIN product p ON p.product_id = v.product_id WHERE sl.sale_id = s.sale_id) AS item_names
                FROM sale s JOIN app_user u ON u.user_id = s.created_by
                LEFT JOIN customer c ON c.customer_id = s.customer_id
                WHERE s.status = 'HELD' ORDER BY s.sale_id""");
    }

    /** Take a held bill back into the cart. The held record is removed so it cannot be resumed twice. */
    @Transactional
    public BillingService.CartRequest resume(Long heldId) {
        Sale s = lockHeld(heldId);
        List<BillingService.CartLine> lines = new ArrayList<>();
        for (SaleLine l : s.getLines()) {
            // Offers are re-applied automatically; only a manual discount needs to be carried over.
            java.math.BigDecimal manual = l.getOfferId() == null ? l.getUnitDiscount() : java.math.BigDecimal.ZERO;
            lines.add(new BillingService.CartLine(l.getVariantId(), l.getQuantity(), manual));
        }
        sales.delete(s);
        return new BillingService.CartRequest(s.getCustomerId(), lines, null);
    }

    @Transactional
    public void discard(Long heldId) {
        sales.delete(lockHeld(heldId));
    }

    private Sale lockHeld(Long heldId) {
        Sale s = sales.lockById(heldId)
                .orElseThrow(() -> ApiException.notFound("ERR-BIL-404", "Held bill not found (it may already be resumed)."));
        if (s.getStatus() != SaleStatus.HELD) {
            throw ApiException.badRequest("ERR-BIL-002", "Bill " + heldId + " is not a held bill.");
        }
        return s;
    }

    private void discardOld() {
        String old = "SELECT sale_id FROM (SELECT sale_id FROM sale WHERE status = 'HELD' "
                + "AND created_at < NOW() - INTERVAL 24 HOUR) x";
        jdbc.update("DELETE FROM sale_line WHERE sale_id IN (" + old + ")");
        jdbc.update("DELETE FROM sale WHERE status = 'HELD' AND created_at < NOW() - INTERVAL 24 HOUR");
    }
}
