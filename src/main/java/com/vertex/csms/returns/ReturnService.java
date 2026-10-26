package com.vertex.csms.returns;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vertex.csms.billing.ReceiptService;
import com.vertex.csms.billing.Sale;
import com.vertex.csms.billing.SaleLine;
import com.vertex.csms.billing.SaleRepository;
import com.vertex.csms.billing.SaleStatus;
import com.vertex.csms.common.ApiException;
import com.vertex.csms.common.DocNumberService;
import com.vertex.csms.common.Money;
import com.vertex.csms.common.RulesProperties;
import com.vertex.csms.customer.Customer;
import com.vertex.csms.customer.CustomerRepository;
import com.vertex.csms.inventory.MovementType;
import com.vertex.csms.inventory.StockService;
import com.vertex.csms.product.Variant;
import com.vertex.csms.product.VariantRepository;
import com.vertex.csms.security.CurrentUser;
import com.vertex.csms.security.OwnerPinService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

/** Returns with credit note (UC-09, REQ-RET-1..6). */
@Service
public class ReturnService {

    public static final List<String> REASONS = List.of("Size issue", "Defect", "Colour mismatch", "Changed mind", "Other");

    private final SaleRepository sales;
    private final VariantRepository variants;
    private final CustomerRepository customers;
    private final CreditNoteRepository creditNotes;
    private final StockService stock;
    private final DocNumberService docNumbers;
    private final OwnerPinService ownerPin;
    private final ReceiptService receipts;
    private final RulesProperties rules;

    public ReturnService(SaleRepository sales, VariantRepository variants, CustomerRepository customers,
                         CreditNoteRepository creditNotes, StockService stock, DocNumberService docNumbers,
                         OwnerPinService ownerPin, ReceiptService receipts, RulesProperties rules) {
        this.sales = sales;
        this.variants = variants;
        this.customers = customers;
        this.creditNotes = creditNotes;
        this.stock = stock;
        this.docNumbers = docNumbers;
        this.ownerPin = ownerPin;
        this.receipts = receipts;
        this.rules = rules;
    }

    public record Lookup(ReceiptService.Receipt bill, long daysSinceSale, int returnWindowDays,
                         boolean needsOwnerPin, boolean returnable, List<String> reasons) {}

    public record ReturnLine(@NotNull Long lineId, @NotNull @Min(1) Integer quantity, boolean defective) {}

    public record ReturnRequest(@NotBlank String invoiceNo, @NotEmpty List<@Valid ReturnLine> lines,
                                @NotBlank(message = "Choose a return reason") String reason, String ownerPin) {}

    public record ReturnResult(String cnNo, BigDecimal amount, LocalDate validTo, String saleStatus, int piecesRestocked) {}

    public Lookup lookup(String invoiceNo) {
        ReceiptService.Receipt bill = receipts.byInvoiceNo(invoiceNo);
        long days = ChronoUnit.DAYS.between(bill.completedAt().toLocalDate(), LocalDate.now());
        boolean open = bill.status().equals("COMPLETED") || bill.status().equals("PARTIALLY_RETURNED");
        return new Lookup(bill, days, rules.returnWindowDays(), days > rules.returnWindowDays(), open, REASONS);
    }

    @Transactional
    public ReturnResult process(ReturnRequest req) {
        Long userId = CurrentUser.id();
        if (!REASONS.contains(req.reason())) {
            throw ApiException.badRequest("ERR-VAL-001", "Choose a reason from the list.", "reason");
        }
        Sale sale = sales.lockByInvoiceNo(req.invoiceNo().trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> ApiException.notFound("ERR-RET-002", "Invoice " + req.invoiceNo() + " not found."));
        if (sale.getStatus() != SaleStatus.COMPLETED && sale.getStatus() != SaleStatus.PARTIALLY_RETURNED) {
            throw ApiException.badRequest("ERR-RET-001", "Bill " + sale.getInvoiceNo() + " is " + sale.getStatus()
                    + "; nothing left to return.");
        }
        long days = ChronoUnit.DAYS.between(sale.getCompletedAt().toLocalDate(), LocalDate.now());
        if (days > rules.returnWindowDays() && !CurrentUser.isOwner() && !ownerPin.matches(req.ownerPin())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "WARN-RET-001", "This bill is " + days + " days old (return window "
                    + rules.returnWindowDays() + " days). The Owner PIN is needed.", "ownerPin");
        }

        Map<Long, SaleLine> linesById = sale.getLines().stream().collect(Collectors.toMap(SaleLine::getId, Function.identity()));
        List<Long> variantIds = req.lines().stream()
                .map(l -> Optional.ofNullable(linesById.get(l.lineId())).map(SaleLine::getVariantId)
                        .orElseThrow(() -> ApiException.badRequest("ERR-RET-001", "Line " + l.lineId() + " is not on this bill.")))
                .distinct().sorted().toList();
        Map<Long, Variant> locked = variants.lockByIds(variantIds).stream()
                .collect(Collectors.toMap(Variant::getId, Function.identity()));

        BigDecimal amount = BigDecimal.ZERO;
        int restocked = 0;
        for (ReturnLine rl : req.lines()) {
            SaleLine line = linesById.get(rl.lineId());
            Variant v = locked.get(line.getVariantId());
            String label = v.getProduct().getName() + " " + v.getSize() + "/" + v.getColour();
            if (!v.getProduct().isReturnable()) {
                throw ApiException.badRequest("ERR-RET-003", label + " is non-returnable (hygiene / custom item).", "lines");
            }
            int left = line.getQuantity() - line.getReturnedQty();
            if (rl.quantity() > left) {
                throw ApiException.badRequest("ERR-RET-001", "Only " + left + " of " + label + " can still be returned.", "lines");
            }
            line.setReturnedQty(line.getReturnedQty() + rl.quantity());
            // Refund the price actually paid per piece (taxable value + GST at the original rate).
            amount = amount.add(line.getUnitPrice().subtract(line.getUnitDiscount()).multiply(BigDecimal.valueOf(rl.quantity())));

            stock.change(v, MovementType.RETURN, rl.quantity(), "SALE", sale.getId(), req.reason(), userId);
            if (rl.defective()) {
                // Defective pieces are written off instead of going back on the shelf (REQ-RET-3).
                stock.change(v, MovementType.ADJUSTMENT, -rl.quantity(), "SALE", sale.getId(),
                        "Defective - not restocked", userId);
            } else {
                restocked += rl.quantity();
            }
        }
        amount = Money.round(amount);

        boolean allReturned = sale.getLines().stream().allMatch(l -> l.getReturnedQty() >= l.getQuantity());
        sale.setStatus(allReturned ? SaleStatus.RETURNED : SaleStatus.PARTIALLY_RETURNED);

        LocalDateTime now = LocalDateTime.now();
        LocalDate validTo = now.toLocalDate().plusDays(rules.creditNoteValidityDays());
        CreditNote cn = creditNotes.save(new CreditNote(docNumbers.nextCreditNoteNo(now.toLocalDate()), sale.getId(),
                amount, validTo, userId, req.reason()));

        // Take back loyalty points earned on the returned value (REQ-RET-6).
        if (sale.getCustomerId() != null) {
            Customer c = customers.lockById(sale.getCustomerId()).orElseThrow();
            int points = amount.divide(Money.HUNDRED, 0, RoundingMode.FLOOR).intValue() * rules.pointsPer100();
            c.setLoyaltyPoints(Math.max(0, c.getLoyaltyPoints() - points));
        }
        return new ReturnResult(cn.getCnNo(), amount, validTo, sale.getStatus().name(), restocked);
    }
}
