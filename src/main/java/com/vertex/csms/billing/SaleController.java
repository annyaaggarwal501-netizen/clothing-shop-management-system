package com.vertex.csms.billing;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/sales")
public class SaleController {

    private final BillingService billing;
    private final HeldBillService held;
    private final VoidService voids;
    private final ReceiptService receipts;
    private final JdbcTemplate jdbc;

    public SaleController(BillingService billing, HeldBillService held, VoidService voids, ReceiptService receipts,
                          JdbcTemplate jdbc) {
        this.billing = billing;
        this.held = held;
        this.voids = voids;
        this.receipts = receipts;
        this.jdbc = jdbc;
    }

    /** Hold Bill (F8). */
    @PostMapping("/hold")
    public HeldBillService.HoldResult hold(@RequestBody @Valid BillingService.CartRequest req) {
        return held.hold(req);
    }

    @GetMapping("/held")
    public List<Map<String, Object>> heldBills() {
        return held.list();
    }

    @PostMapping("/held/{id}/resume")
    public BillingService.CartRequest resume(@PathVariable Long id) {
        return held.resume(id);
    }

    @DeleteMapping("/held/{id}")
    public void discardHeld(@PathVariable Long id) {
        held.discard(id);
    }

    /** Void a bill made today: Owner, or Staff with the Owner PIN (REQ-BIL-9). */
    @PostMapping("/{id}/void")
    public ReceiptService.Receipt voidSale(@PathVariable Long id, @RequestBody VoidService.VoidRequest req) {
        voids.voidSale(id, req);
        return receipts.bySaleId(id, false);
    }

    /** Price the cart (offers, GST, round-off) without saving anything. Called on every cart change. */
    @PostMapping("/quote")
    public BillingService.Quote quote(@RequestBody @Valid BillingService.CartRequest req) {
        return billing.quote(req);
    }

    /** Complete the sale: one ACID transaction (REQ-BIL-7). */
    @PostMapping
    public BillingService.CompleteResult complete(@RequestBody @Valid BillingService.CompleteRequest req) {
        return billing.complete(req);
    }

    @GetMapping("/{id}")
    public ReceiptService.Receipt get(@PathVariable Long id) {
        return receipts.bySaleId(id, false);
    }

    @GetMapping("/by-invoice/{invoiceNo}")
    public ReceiptService.Receipt byInvoice(@PathVariable String invoiceNo) {
        return receipts.byInvoiceNo(invoiceNo);
    }

    /** Bills of one day (default today), newest first. */
    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate day = date == null ? LocalDate.now() : date;
        return jdbc.queryForList("""
                SELECT s.sale_id, s.invoice_no, s.status, s.completed_at, s.grand_total, s.discount_total,
                       c.name AS customer_name, u.full_name AS cashier,
                       (SELECT SUM(quantity) FROM sale_line sl WHERE sl.sale_id = s.sale_id) AS items
                FROM sale s JOIN app_user u ON u.user_id = s.created_by
                LEFT JOIN customer c ON c.customer_id = s.customer_id
                WHERE s.status <> 'HELD' AND s.completed_at >= ? AND s.completed_at < ?
                ORDER BY s.sale_id DESC""", day.atStartOfDay(), day.plusDays(1).atStartOfDay());
    }
}
