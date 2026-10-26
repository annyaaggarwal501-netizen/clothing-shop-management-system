package com.vertex.csms.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vertex.csms.common.ApiException;
import com.vertex.csms.common.Money;
import com.vertex.csms.common.RulesProperties;
import com.vertex.csms.customer.Customer;
import com.vertex.csms.customer.CustomerRepository;
import com.vertex.csms.inventory.AlertService;
import com.vertex.csms.inventory.MovementType;
import com.vertex.csms.inventory.StockService;
import com.vertex.csms.product.Variant;
import com.vertex.csms.product.VariantRepository;
import com.vertex.csms.returns.CreditNoteRepository;
import com.vertex.csms.security.CurrentUser;
import com.vertex.csms.security.OwnerPinService;

/**
 * Void (cancel) a completed bill (REQ-BIL-9): Owner only (or Staff with the Owner PIN), same business day,
 * with a reason. Everything the sale did is reversed in one transaction: stock comes back through VOID
 * movements, loyalty points and credit due are taken back, and credit notes used as payment get their balance back.
 * The invoice number stays used (status CANCELLED), so the invoice series has no gaps for GST.
 */
@Service
public class VoidService {

    private final SaleRepository sales;
    private final VariantRepository variants;
    private final CustomerRepository customers;
    private final CreditNoteRepository creditNotes;
    private final StockService stock;
    private final OwnerPinService ownerPin;
    private final AlertService alerts;
    private final RulesProperties rules;

    public VoidService(SaleRepository sales, VariantRepository variants, CustomerRepository customers,
                       CreditNoteRepository creditNotes, StockService stock, OwnerPinService ownerPin,
                       AlertService alerts, RulesProperties rules) {
        this.sales = sales;
        this.variants = variants;
        this.customers = customers;
        this.creditNotes = creditNotes;
        this.stock = stock;
        this.ownerPin = ownerPin;
        this.alerts = alerts;
        this.rules = rules;
    }

    public record VoidRequest(String reason, String ownerPin) {}

    @Transactional
    public void voidSale(Long saleId, VoidRequest req) {
        ownerPin.requireOwnerApproval(req.ownerPin(), "Voiding a bill");
        String reason = req.reason() == null ? "" : req.reason().trim();
        if (reason.length() < 3 || reason.length() > 100) {
            throw ApiException.badRequest("ERR-VAL-001", "Give a reason for voiding the bill (3-100 characters).", "reason");
        }
        Long userId = CurrentUser.id();
        Sale sale = sales.lockById(saleId).orElseThrow(() -> ApiException.notFound("ERR-RET-002", "Bill not found."));
        if (sale.getStatus() != SaleStatus.COMPLETED) {
            throw ApiException.badRequest("ERR-BIL-002", "Only a completed bill with no returns can be voided. This bill is "
                    + sale.getStatus() + ".");
        }
        if (!sale.getCompletedAt().toLocalDate().equals(LocalDate.now())) {
            throw ApiException.badRequest("ERR-BIL-002", "A bill can only be voided on the day it was made. "
                    + "Use Returns for older bills.");
        }

        // 1. Stock back on the shelf.
        Map<Long, Variant> locked = variants.lockByIds(sale.getLines().stream().map(SaleLine::getVariantId)
                .distinct().sorted().toList()).stream().collect(Collectors.toMap(Variant::getId, Function.identity()));
        for (SaleLine l : sale.getLines()) {
            stock.change(locked.get(l.getVariantId()), MovementType.VOID, l.getQuantity(), "SALE", sale.getId(),
                    "Void: " + reason, userId);
        }

        // 2. Undo payments that changed balances.
        Customer customer = sale.getCustomerId() == null ? null : customers.lockById(sale.getCustomerId()).orElse(null);
        BigDecimal creditNotePaid = BigDecimal.ZERO;
        for (Payment p : sale.getPayments()) {
            if (p.getMode() == PaymentMode.CREDIT && customer != null) {
                customer.setCreditDue(customer.getCreditDue().subtract(p.getAmount()).max(BigDecimal.ZERO));
            } else if (p.getMode() == PaymentMode.CREDIT_NOTE && p.getReference() != null) {
                creditNotes.lockByCnNo(p.getReference()).ifPresent(cn ->
                        cn.setBalance(cn.getBalance().add(p.getAmount()).min(cn.getAmount())));
                creditNotePaid = creditNotePaid.add(p.getAmount());
            }
        }

        // 3. Take back the loyalty points this bill earned (same formula as BillingService).
        if (customer != null) {
            int points = sale.getGrandTotal().subtract(creditNotePaid).max(BigDecimal.ZERO)
                    .divide(Money.HUNDRED, 0, RoundingMode.FLOOR).intValue() * rules.pointsPer100();
            customer.setLoyaltyPoints(Math.max(0, customer.getLoyaltyPoints() - points));
        }

        sale.markVoided(reason, userId);
        alerts.raise("VOID_BILL", "WARN", sale.getId(), "Bill " + sale.getInvoiceNo() + " (Rs " + sale.getGrandTotal()
                + ") voided by " + CurrentUser.get().fullName() + ": " + reason);
    }
}
