package com.vertex.csms.billing;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.vertex.csms.common.ApiException;
import com.vertex.csms.common.Money;
import com.vertex.csms.common.ShopProperties;

/** Read model of a saved bill: used by the printed receipt, the e-bill page and the returns screen. */
@Service
public class ReceiptService {

    private final JdbcTemplate jdbc;
    private final ShopProperties shop;

    public ReceiptService(JdbcTemplate jdbc, ShopProperties shop) {
        this.jdbc = jdbc;
        this.shop = shop;
    }

    public record Line(Long lineId, Long variantId, String productName, String size, String colour, String sku,
                       String hsnCode, int quantity, int returnedQty, BigDecimal unitPrice, BigDecimal unitDiscount,
                       String offerName, BigDecimal gstRate, BigDecimal taxableValue, BigDecimal taxAmount,
                       BigDecimal lineTotal, boolean returnable) {}

    public record PaymentRow(String mode, BigDecimal amount, String reference) {}

    public record CreditNoteRow(String cnNo, BigDecimal amount, BigDecimal balance, LocalDate validTo,
                                LocalDateTime createdAt, String reason) {}

    public record Receipt(Long saleId, String invoiceNo, String status, LocalDateTime completedAt, String cashier,
                          Long customerId, String customerName, String customerMobile, boolean customerOptedIn,
                          List<Line> lines, List<BillingService.RateRow> taxSummary, BigDecimal subtotal,
                          BigDecimal discountTotal, BigDecimal taxableTotal, BigDecimal cgstTotal, BigDecimal sgstTotal,
                          BigDecimal roundOff, BigDecimal grandTotal, List<PaymentRow> payments,
                          List<CreditNoteRow> creditNotes, ShopProperties shop, String voidReason,
                          LocalDateTime voidedAt) {}

    public Receipt bySaleId(Long saleId, boolean maskMobile) {
        List<Receipt> found = jdbc.query("""
                SELECT s.*, u.full_name AS cashier, c.name AS customer_name, c.mobile, c.wa_opt_in
                FROM sale s JOIN app_user u ON u.user_id = s.created_by
                LEFT JOIN customer c ON c.customer_id = s.customer_id
                WHERE s.sale_id = ?""", (rs, i) -> header(rs, maskMobile), saleId);
        if (found.isEmpty()) {
            throw ApiException.notFound("ERR-RET-002", "Bill not found.");
        }
        return found.get(0);
    }

    public Receipt byInvoiceNo(String invoiceNo) {
        List<Long> ids = jdbc.queryForList("SELECT sale_id FROM sale WHERE invoice_no = ? AND status <> 'HELD'", Long.class,
                invoiceNo.trim().toUpperCase());
        if (ids.isEmpty()) {
            throw ApiException.notFound("ERR-RET-002", "Invoice " + invoiceNo + " not found.");
        }
        return bySaleId(ids.get(0), false);
    }

    private Receipt header(ResultSet rs, boolean maskMobile) throws SQLException {
        long saleId = rs.getLong("sale_id");
        List<Line> lines = jdbc.query("""
                SELECT sl.*, v.sku, v.size, v.colour, p.name AS product_name, p.hsn_code, p.is_returnable,
                       o.name AS offer_name
                FROM sale_line sl JOIN variant v ON v.variant_id = sl.variant_id
                JOIN product p ON p.product_id = v.product_id
                LEFT JOIN offer o ON o.offer_id = sl.offer_id
                WHERE sl.sale_id = ? ORDER BY sl.line_id""", (r, i) -> new Line(
                r.getLong("line_id"), r.getLong("variant_id"), r.getString("product_name"), r.getString("size"),
                r.getString("colour"), r.getString("sku"), r.getString("hsn_code"), r.getInt("quantity"),
                r.getInt("returned_qty"), r.getBigDecimal("unit_price"), r.getBigDecimal("unit_discount"),
                r.getString("offer_name"), r.getBigDecimal("gst_rate"), r.getBigDecimal("taxable_value"),
                r.getBigDecimal("tax_amount"),
                Money.round(r.getBigDecimal("unit_price").subtract(r.getBigDecimal("unit_discount"))
                        .multiply(BigDecimal.valueOf(r.getInt("quantity")))),
                r.getBoolean("is_returnable")), saleId);

        Map<BigDecimal, BigDecimal[]> byRate = new TreeMap<>();
        for (Line l : lines) {
            BigDecimal[] acc = byRate.computeIfAbsent(l.gstRate(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            acc[0] = acc[0].add(l.taxableValue());
            acc[1] = acc[1].add(l.taxAmount());
        }
        List<BillingService.RateRow> summary = new ArrayList<>();
        byRate.forEach((rate, acc) -> {
            TaxCalculator.Split split = TaxCalculator.split(acc[1]);
            summary.add(new BillingService.RateRow(rate, acc[0], split.cgst(), split.sgst(), acc[1]));
        });

        List<PaymentRow> payments = jdbc.query("SELECT mode, amount, reference FROM payment WHERE sale_id = ?",
                (r, i) -> new PaymentRow(r.getString("mode"), r.getBigDecimal("amount"), r.getString("reference")), saleId);
        List<CreditNoteRow> cns = jdbc.query(
                "SELECT cn_no, amount, balance, valid_to, created_at, reason FROM credit_note WHERE sale_id = ? ORDER BY cn_id",
                (r, i) -> new CreditNoteRow(r.getString("cn_no"), r.getBigDecimal("amount"), r.getBigDecimal("balance"),
                        r.getObject("valid_to", LocalDate.class), r.getObject("created_at", LocalDateTime.class),
                        r.getString("reason")), saleId);

        String mobile = rs.getString("mobile");
        if (mobile != null && maskMobile) {
            mobile = mobile.substring(0, 2) + "XXXXXX" + mobile.substring(8);
        }
        Long customerId = rs.getObject("customer_id") == null ? null : rs.getLong("customer_id");
        return new Receipt(saleId, rs.getString("invoice_no"), rs.getString("status"),
                rs.getObject("completed_at", LocalDateTime.class), rs.getString("cashier"), customerId,
                rs.getString("customer_name"), mobile, rs.getBoolean("wa_opt_in"), lines, summary,
                rs.getBigDecimal("subtotal"), rs.getBigDecimal("discount_total"), rs.getBigDecimal("taxable_total"),
                rs.getBigDecimal("cgst_total"), rs.getBigDecimal("sgst_total"), rs.getBigDecimal("round_off"),
                rs.getBigDecimal("grand_total"), payments, cns, shop, rs.getString("void_reason"),
                rs.getObject("voided_at", LocalDateTime.class));
    }
}
