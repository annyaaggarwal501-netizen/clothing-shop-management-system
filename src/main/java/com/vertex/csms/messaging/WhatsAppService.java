package com.vertex.csms.messaging;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vertex.csms.billing.Offer;
import com.vertex.csms.billing.OfferService;
import com.vertex.csms.billing.ReceiptService;
import com.vertex.csms.common.ApiException;
import com.vertex.csms.common.ShopProperties;
import com.vertex.csms.customer.Customer;
import com.vertex.csms.customer.CustomerRepository;
import com.vertex.csms.product.Product;
import com.vertex.csms.product.ProductRepository;
import com.vertex.csms.product.Variant;

/**
 * WhatsApp in Click-to-Chat mode (REQ-WA-1): the app builds a wa.me link with the message filled in;
 * WhatsApp opens on the phone/PC and staff press Send. No API account or cost.
 *
 * Consent rule (REQ-SAF-3, ERR-WA-001): the app never builds a link to a customer who has not opted in.
 * The 30 dummy customers are all opted out, so they can never be messaged.
 */
@Service
public class WhatsAppService {

    private final CustomerRepository customers;
    private final ProductRepository products;
    private final OfferService offers;
    private final ReceiptService receipts;
    private final BillLinkService links;
    private final WaMessageRepository log;
    private final ShopProperties shop;

    public WhatsAppService(CustomerRepository customers, ProductRepository products, OfferService offers,
                           ReceiptService receipts, BillLinkService links, WaMessageRepository log, ShopProperties shop) {
        this.customers = customers;
        this.products = products;
        this.offers = offers;
        this.receipts = receipts;
        this.links = links;
        this.log = log;
        this.shop = shop;
    }

    public record ShareLink(String waUrl, String text, String ebillUrl, String recipient) {}

    public record ProductShare(Long productId, String name, String text, String waUrl, BigDecimal price,
                               BigDecimal offerPrice, String offerName, List<String> sizes, List<String> colours,
                               String recipient) {}

    /** E-bill for a completed sale, sent to the bill's customer. */
    @Transactional
    public ShareLink ebill(Long saleId, String baseUrl) {
        ReceiptService.Receipt r = receipts.bySaleId(saleId, false);
        if (r.customerId() == null) {
            throw ApiException.badRequest("ERR-WA-001", "This is a walk-in bill with no customer, so there is no one to send it to.");
        }
        Customer c = customers.findById(r.customerId()).orElseThrow();
        requireConsent(c);
        String ebillUrl = baseUrl + "/ebill.html?t=" + links.token(saleId);
        String text = "Namaste " + c.getName().replace(" (TEST)", "") + " ji,\n"
                + "Thank you for shopping at *" + shop.name() + "*.\n"
                + "Bill " + r.invoiceNo() + " - Rs " + r.grandTotal().toPlainString() + "\n"
                + "View / download your e-bill: " + ebillUrl + "\n"
                + "Exchange within 7 days with this bill.";
        log.save(WaMessage.clickToChat(c.getId(), "E_BILL"));
        return new ShareLink(waUrl(c.getMobile(), text), text, ebillUrl, c.getName());
    }

    /** Product message (REQ-WA-2). With customerId it goes to that (opted-in) customer; without, staff pick the chat. */
    @Transactional(readOnly = true)
    public ProductShare product(Long productId, Long customerId) {
        Product p = products.findById(productId).orElseThrow(() -> ApiException.notFound("ERR-PRD-004", "Product not found."));
        Set<String> sizes = new LinkedHashSet<>();
        Set<String> colours = new LinkedHashSet<>();
        BigDecimal lowest = null;
        for (Variant v : p.getVariants()) {
            if (v.getStockQty() > 0) {
                sizes.add(v.getSize());
                colours.add(v.getColour());
                lowest = lowest == null ? v.getSellingPrice() : lowest.min(v.getSellingPrice());
            }
        }
        if (lowest == null) {
            throw ApiException.badRequest("ERR-INV-001", "This product is out of stock in every size, so it cannot be shared.");
        }
        BigDecimal price = lowest;
        List<Offer> running = offers.running(LocalDateTime.now());
        var offer = offers.best(running, p, price);
        BigDecimal offerPrice = offer.map(o -> price.subtract(o.unitDiscount())).orElse(null);

        StringBuilder text = new StringBuilder();
        text.append("*").append(p.getName()).append("*");
        if (p.getBrand() != null) text.append(" - ").append(p.getBrand());
        text.append("\nSizes: ").append(String.join(", ", sizes));
        text.append("\nColours: ").append(String.join(", ", colours));
        text.append("\nPrice: Rs ").append(price.stripTrailingZeros().toPlainString());
        if (offerPrice != null) {
            text.append("\nOffer: ").append(offer.get().name()).append(" - now Rs ")
                .append(offerPrice.stripTrailingZeros().toPlainString());
        }
        text.append("\n\nVisit ").append(shop.name()).append(", ").append(shop.address());
        text.append("\nLocation: ").append(shop.locationUrl());

        String recipient = null;
        String url;
        if (customerId != null) {
            Customer c = customers.findById(customerId).orElseThrow(() -> ApiException.notFound("INFO-CUS-001", "Customer not found."));
            requireConsent(c);
            url = waUrl(c.getMobile(), text.toString());
            recipient = c.getName();
        } else {
            url = "https://wa.me/?text=" + enc(text.toString());
        }
        return new ProductShare(p.getId(), p.getName(), text.toString(), url, price, offerPrice,
                offer.map(OfferService.AppliedOffer::name).orElse(null), List.copyOf(sizes), List.copyOf(colours), recipient);
    }

    @Transactional
    public void logProductShare(Long customerId) {
        Customer c = customers.findById(customerId).orElseThrow(() -> ApiException.notFound("INFO-CUS-001", "Customer not found."));
        requireConsent(c);
        log.save(WaMessage.clickToChat(c.getId(), "PRODUCT_SHARE"));
    }

    private static void requireConsent(Customer c) {
        if (!c.isWaOptIn()) {
            throw ApiException.forbidden("ERR-WA-001", c.getName() + " has not opted in to WhatsApp messages. "
                    + "Record their consent on the Customers screen first.");
        }
    }

    private static String waUrl(String mobile, String text) {
        return "https://wa.me/91" + mobile + "?text=" + enc(text);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
