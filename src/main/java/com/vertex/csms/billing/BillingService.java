package com.vertex.csms.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vertex.csms.common.ApiException;
import com.vertex.csms.common.DocNumberService;
import com.vertex.csms.common.Money;
import com.vertex.csms.common.RulesProperties;
import com.vertex.csms.customer.Customer;
import com.vertex.csms.customer.CustomerRepository;
import com.vertex.csms.inventory.AlertService;
import com.vertex.csms.inventory.MovementType;
import com.vertex.csms.inventory.StockService;
import com.vertex.csms.product.Product;
import com.vertex.csms.product.TaxClass;
import com.vertex.csms.product.Variant;
import com.vertex.csms.product.VariantRepository;
import com.vertex.csms.returns.CreditNote;
import com.vertex.csms.returns.CreditNoteRepository;
import com.vertex.csms.security.CurrentUser;
import com.vertex.csms.security.OwnerPinService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

/**
 * POS billing (UC-08). quote() prices the cart for the screen; complete() saves the sale.
 * Both use the same price() method, so the screen and the saved bill can never disagree.
 */
@Service
public class BillingService {

    private final VariantRepository variants;
    private final CustomerRepository customers;
    private final SaleRepository sales;
    private final CreditNoteRepository creditNotes;
    private final GstSlabService slabService;
    private final OfferService offerService;
    private final StockService stock;
    private final DocNumberService docNumbers;
    private final OwnerPinService ownerPin;
    private final AlertService alerts;
    private final RulesProperties rules;

    public BillingService(VariantRepository variants, CustomerRepository customers, SaleRepository sales,
                          CreditNoteRepository creditNotes, GstSlabService slabService, OfferService offerService,
                          StockService stock, DocNumberService docNumbers, OwnerPinService ownerPin,
                          AlertService alerts, RulesProperties rules) {
        this.variants = variants;
        this.customers = customers;
        this.sales = sales;
        this.creditNotes = creditNotes;
        this.slabService = slabService;
        this.offerService = offerService;
        this.stock = stock;
        this.docNumbers = docNumbers;
        this.ownerPin = ownerPin;
        this.alerts = alerts;
        this.rules = rules;
    }

    // ---------- request / response shapes ----------

    public record CartLine(@NotNull Long variantId,
                           @NotNull @Min(value = 1, message = "Quantity must be at least 1") @Max(999) Integer quantity,
                           @DecimalMin(value = "0.00", message = "Discount cannot be negative") BigDecimal manualDiscount) {}

    public record CartRequest(Long customerId, @NotNull List<@Valid CartLine> lines, String ownerPin) {}

    public record PaymentRequest(@NotNull PaymentMode mode,
                                 @NotNull @DecimalMin(value = "0.01", message = "Payment amount must be more than 0") BigDecimal amount,
                                 @Size(max = 50) String reference) {}

    public record CompleteRequest(Long customerId, @NotNull List<@Valid CartLine> lines,
                                  @NotNull List<@Valid PaymentRequest> payments, String ownerPin) {}

    public record QuoteLine(Long variantId, String sku, String barcode, String productName, String size, String colour,
                            String hsnCode, TaxClass taxClass, int quantity, int stockQty, boolean enoughStock,
                            BigDecimal unitPrice, BigDecimal mrp, Long offerId, String offerName,
                            BigDecimal manualDiscount, BigDecimal unitDiscount, BigDecimal unitNet, BigDecimal gstRate,
                            BigDecimal taxableValue, BigDecimal taxAmount, BigDecimal lineTotal, boolean slabWarning) {}

    public record RateRow(BigDecimal rate, BigDecimal taxable, BigDecimal cgst, BigDecimal sgst, BigDecimal tax) {}

    public record CustomerSummary(Long id, String name, String mobile, boolean waOptIn, int loyaltyPoints,
                                  BigDecimal creditLimit, BigDecimal creditDue) {
        static CustomerSummary of(Customer c) {
            return c == null ? null : new CustomerSummary(c.getId(), c.getName(), c.getMobile(), c.isWaOptIn(),
                    c.getLoyaltyPoints(), c.getCreditLimit(), c.getCreditDue());
        }
    }

    public record Quote(List<QuoteLine> lines, List<RateRow> taxSummary, int totalQty, BigDecimal subtotal,
                        BigDecimal discountTotal, BigDecimal taxableTotal, BigDecimal cgstTotal, BigDecimal sgstTotal,
                        BigDecimal exactTotal, BigDecimal roundOff, BigDecimal grandTotal, boolean needsOwnerPin,
                        List<String> warnings, CustomerSummary customer) {}

    public record CompleteResult(Long saleId, String invoiceNo, BigDecimal grandTotal, int pointsEarned,
                                 boolean customerOptedIn, List<String> warnings, List<String> stockAlerts) {}

    // ---------- quote ----------

    @Transactional(readOnly = true)
    public Quote quote(CartRequest req) {
        List<Long> ids = req.lines().stream().map(CartLine::variantId).distinct().toList();
        Map<Long, Variant> map = variants.findAllById(ids).stream()
                .collect(Collectors.toMap(Variant::getId, Function.identity()));
        Customer customer = req.customerId() == null ? null : customers.findById(req.customerId()).orElse(null);
        Quote q = price(req.lines(), map, customer, LocalDateTime.now());
        if (q.needsOwnerPin() && !CurrentUser.isOwner()) {
            q.warnings().add("ERR-BIL-005: a manual discount is above the staff limit of "
                    + rules.staffDiscountLimitPercent().stripTrailingZeros().toPlainString()
                    + "%. The Owner PIN is needed to complete the sale.");
        }
        return q;
    }

    // ---------- complete (the all-or-nothing save, REQ-BIL-7) ----------

    @Transactional
    public CompleteResult complete(CompleteRequest req) {
        if (req.lines().isEmpty()) {
            throw ApiException.badRequest("ERR-BIL-001", "The cart is empty. Scan at least one item.");
        }
        Long userId = CurrentUser.id();
        LocalDateTime now = LocalDateTime.now();

        // 1. Lock every variant in the cart (SELECT ... FOR UPDATE). A second counter selling the same
        //    piece waits here until we commit, then sees the reduced stock.
        List<Long> ids = req.lines().stream().map(CartLine::variantId).distinct().sorted().toList();
        Map<Long, Variant> locked = variants.lockByIds(ids).stream()
                .collect(Collectors.toMap(Variant::getId, Function.identity()));

        Customer customer = null;
        if (req.customerId() != null) {
            customer = customers.lockById(req.customerId())
                    .orElseThrow(() -> ApiException.badRequest("ERR-VAL-001", "Customer not found", "customerId"));
        }

        // 2. Price the cart again on the server - never trust totals sent by the browser.
        Quote q = price(req.lines(), locked, customer, now);
        if (q.needsOwnerPin() && !CurrentUser.isOwner() && !ownerPin.matches(req.ownerPin())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ERR-BIL-005", "Discount is above the staff limit of "
                    + rules.staffDiscountLimitPercent().stripTrailingZeros().toPlainString()
                    + "%. Ask the Owner to enter the PIN.", "ownerPin");
        }

        // 3. Payments must add up to the rounded grand total (REQ-BIL-6).
        BigDecimal paid = req.payments().stream().map(PaymentRequest::amount).map(Money::round)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (paid.compareTo(q.grandTotal()) != 0) {
            throw ApiException.badRequest("ERR-BIL-003", "Payments add up to Rs " + paid + " but the bill total is Rs "
                    + q.grandTotal() + ".", "payments");
        }

        // 4. Insert sale, lines and payments.
        Sale sale = new Sale();
        sale.setCustomerId(customer == null ? null : customer.getId());
        sale.setCreatedBy(userId);
        sale.setStatus(SaleStatus.COMPLETED);
        sale.setCompletedAt(now);
        sale.setSubtotal(q.subtotal());
        sale.setDiscountTotal(q.discountTotal());
        sale.setTaxableTotal(q.taxableTotal());
        sale.setCgstTotal(q.cgstTotal());
        sale.setSgstTotal(q.sgstTotal());
        sale.setRoundOff(q.roundOff());
        sale.setGrandTotal(q.grandTotal());
        sale.setInvoiceNo(docNumbers.nextInvoiceNo(now.toLocalDate()));
        for (QuoteLine l : q.lines()) {
            sale.getLines().add(new SaleLine(sale, l.variantId(), l.offerId(), l.quantity(), l.unitPrice(),
                    l.unitDiscount(), l.gstRate(), l.taxableValue(), l.taxAmount()));
        }

        List<String> warnings = new ArrayList<>(q.warnings());
        BigDecimal creditNotePaid = BigDecimal.ZERO;
        for (PaymentRequest p : req.payments()) {
            BigDecimal amount = Money.round(p.amount());
            String ref = p.reference() == null || p.reference().isBlank() ? null : p.reference().trim();
            switch (p.mode()) {
                case CREDIT -> {
                    if (customer == null) {
                        throw ApiException.badRequest("ERR-VAL-001", "A credit (udhaar) sale needs a customer.", "payments");
                    }
                    customer.setCreditDue(customer.getCreditDue().add(amount));
                    if (customer.getCreditDue().compareTo(customer.getCreditLimit()) > 0) {
                        warnings.add("WARN-CUS-001: " + customer.getName() + " is now over the credit limit (due Rs "
                                + customer.getCreditDue() + ", limit Rs " + customer.getCreditLimit() + ").");
                        alerts.raise("CREDIT_LIMIT", "WARN", customer.getId(), "Credit limit exceeded: "
                                + customer.getName() + " due Rs " + customer.getCreditDue());
                    }
                }
                case CREDIT_NOTE -> {
                    redeemCreditNote(ref, amount, now.toLocalDate());
                    creditNotePaid = creditNotePaid.add(amount);
                }
                case POINTS -> throw ApiException.badRequest("ERR-VAL-001", "Paying with points is not in the prototype.", "payments");
                default -> { }
            }
            sale.getPayments().add(new Payment(sale, p.mode(), amount, ref));
        }
        sales.save(sale);

        // 5. Deduct stock with SALE movements. Throws ERR-INV-001 if any line is short -> everything rolls back.
        for (QuoteLine l : q.lines()) {
            stock.change(locked.get(l.variantId()), MovementType.SALE, -l.quantity(), "SALE", sale.getId(),
                    sale.getInvoiceNo(), userId);
        }

        // 6. Loyalty: 1 point per Rs 100 paid (credit-note value excluded) (REQ-CUS-4).
        int points = 0;
        if (customer != null) {
            BigDecimal earnBase = q.grandTotal().subtract(creditNotePaid).max(BigDecimal.ZERO);
            points = earnBase.divide(Money.HUNDRED, 0, RoundingMode.FLOOR).intValue() * rules.pointsPer100();
            customer.setLoyaltyPoints(customer.getLoyaltyPoints() + points);
        }

        if (q.subtotal().signum() > 0
                && q.discountTotal().compareTo(Money.percent(q.subtotal(), new BigDecimal("25"))) > 0) {
            alerts.raise("HIGH_DISCOUNT", "WARN", sale.getId(), "Bill " + sale.getInvoiceNo()
                    + " has discount Rs " + q.discountTotal() + " on Rs " + q.subtotal());
        }

        List<String> stockAlerts = locked.values().stream()
                .filter(v -> v.getStockQty() <= v.getReorderLevel())
                .map(v -> (v.getStockQty() == 0 ? "OUT OF STOCK: " : "LOW STOCK: ") + v.getProduct().getName()
                        + " " + v.getSize() + "/" + v.getColour() + " - " + v.getStockQty() + " left")
                .toList();

        return new CompleteResult(sale.getId(), sale.getInvoiceNo(), sale.getGrandTotal(), points,
                customer != null && customer.isWaOptIn(), warnings, stockAlerts);
    }

    private void redeemCreditNote(String cnNo, BigDecimal amount, LocalDate today) {
        if (cnNo == null) {
            throw ApiException.badRequest("ERR-VAL-001", "Enter the credit note number.", "payments");
        }
        CreditNote cn = creditNotes.lockByCnNo(cnNo.toUpperCase(Locale.ROOT))
                .orElseThrow(() -> ApiException.badRequest("ERR-VAL-001", "Credit note " + cnNo + " not found.", "payments"));
        if (cn.getValidTo().isBefore(today)) {
            throw ApiException.badRequest("ERR-VAL-001", "Credit note " + cnNo + " expired on " + cn.getValidTo() + ".", "payments");
        }
        if (cn.getBalance().compareTo(amount) < 0) {
            throw ApiException.badRequest("ERR-VAL-001", "Credit note " + cnNo + " has only Rs " + cn.getBalance() + " left.", "payments");
        }
        cn.setBalance(cn.getBalance().subtract(amount));
    }

    // ---------- pricing shared by quote() and complete() ----------

    private Quote price(List<CartLine> cart, Map<Long, Variant> variantMap, Customer customer, LocalDateTime now) {
        List<Offer> running = offerService.running(now);
        Map<TaxClass, List<TaxCalculator.Slab>> slabCache = new EnumMap<>(TaxClass.class);
        BigDecimal staffLimit = rules.staffDiscountLimitPercent();

        Map<Long, Integer> qtyPerVariant = new HashMap<>();
        cart.forEach(l -> qtyPerVariant.merge(l.variantId(), l.quantity(), Integer::sum));

        List<QuoteLine> lines = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        boolean needsPin = false;
        BigDecimal subtotal = BigDecimal.ZERO, discountTotal = BigDecimal.ZERO, taxableTotal = BigDecimal.ZERO;
        Map<BigDecimal, BigDecimal[]> byRate = new TreeMap<>();   // rate -> {taxable, tax}
        int totalQty = 0;

        for (CartLine line : cart) {
            Variant v = variantMap.get(line.variantId());
            if (v == null || !v.getProduct().isActive()) {
                throw ApiException.notFound("ERR-PRD-004", "Item " + line.variantId() + " not found or inactive.");
            }
            Product p = v.getProduct();
            BigDecimal unitPrice = v.getSellingPrice();
            String label = p.getName() + " " + v.getSize() + "/" + v.getColour();

            // Discount before tax: best offer or the manual discount, whichever is larger (no stacking).
            Optional<OfferService.AppliedOffer> offer = offerService.best(running, p, unitPrice);
            BigDecimal manual = Money.round(Money.nz(line.manualDiscount()));
            if (manual.compareTo(unitPrice) >= 0) {
                throw ApiException.badRequest("ERR-VAL-001", "Discount on " + label + " must be less than its price.", "manualDiscount");
            }
            BigDecimal unitDiscount = Money.ZERO;
            Long offerId = null;
            String offerName = null;
            if (offer.isPresent() && offer.get().unitDiscount().compareTo(manual) >= 0) {
                unitDiscount = offer.get().unitDiscount();
                offerId = offer.get().offerId();
                offerName = offer.get().name();
            } else if (manual.signum() > 0) {
                unitDiscount = manual;
                if (offer.isPresent()) {
                    warnings.add("WARN-OFR-001: offers do not stack on " + label + "; the larger manual discount was used.");
                }
            }
            if (manual.compareTo(Money.percent(unitPrice, staffLimit)) > 0) {
                needsPin = true;
            }

            BigDecimal unitNet = unitPrice.subtract(unitDiscount);
            List<TaxCalculator.Slab> slabs = slabCache.computeIfAbsent(p.getTaxClass(),
                    tc -> slabService.slabs(tc, now.toLocalDate()));
            TaxCalculator.LineTax tax = TaxCalculator.compute(slabs, unitNet, line.quantity());
            boolean slabWarn = TaxCalculator.isSlabSensitive(slabs, unitNet);
            if (slabWarn) {
                warnings.add("WARN-GST-001: " + label + " at Rs " + unitNet + " is in the price band where a small"
                        + " discount changes GST from 18% to 5%. Owner may review this price.");
            }

            int wanted = qtyPerVariant.get(v.getId());
            boolean enough = v.getStockQty() >= wanted;
            if (!enough) {
                warnings.add("ERR-INV-001: only " + v.getStockQty() + " of " + label + " in stock.");
            }

            BigDecimal qty = BigDecimal.valueOf(line.quantity());
            BigDecimal lineTotal = Money.round(unitNet.multiply(qty));
            lines.add(new QuoteLine(v.getId(), v.getSku(), v.getBarcode(), p.getName(), v.getSize(), v.getColour(),
                    p.getHsnCode(), p.getTaxClass(), line.quantity(), v.getStockQty(), enough, unitPrice, v.getMrp(),
                    offerId, offerName, manual, unitDiscount, unitNet, tax.rate(), tax.taxableValue(),
                    tax.taxAmount(), lineTotal, slabWarn));

            subtotal = subtotal.add(unitPrice.multiply(qty));
            discountTotal = discountTotal.add(unitDiscount.multiply(qty));
            taxableTotal = taxableTotal.add(tax.taxableValue());
            BigDecimal[] acc = byRate.computeIfAbsent(tax.rate().setScale(2), r -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            acc[0] = acc[0].add(tax.taxableValue());
            acc[1] = acc[1].add(tax.taxAmount());
            totalQty += line.quantity();
        }

        // Rate-wise CGST/SGST summary (REQ-BIL-5).
        List<RateRow> summary = new ArrayList<>();
        BigDecimal cgst = BigDecimal.ZERO, sgst = BigDecimal.ZERO;
        for (var e : byRate.entrySet()) {
            TaxCalculator.Split split = TaxCalculator.split(e.getValue()[1]);
            summary.add(new RateRow(e.getKey(), Money.round(e.getValue()[0]), split.cgst(), split.sgst(), Money.round(e.getValue()[1])));
            cgst = cgst.add(split.cgst());
            sgst = sgst.add(split.sgst());
        }

        subtotal = Money.round(subtotal);
        discountTotal = Money.round(discountTotal);
        BigDecimal exact = subtotal.subtract(discountTotal);
        BigDecimal grand = exact.setScale(0, RoundingMode.HALF_UP).setScale(2);
        return new Quote(lines, summary, totalQty, subtotal, discountTotal, Money.round(taxableTotal),
                Money.round(cgst), Money.round(sgst), exact, grand.subtract(exact), grand, needsPin, warnings,
                CustomerSummary.of(customer));
    }
}
