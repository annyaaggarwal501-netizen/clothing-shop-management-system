package com.vertex.csms.billing;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.vertex.csms.common.Money;
import com.vertex.csms.product.Product;

/**
 * Item-level offers (REQ-OFR-1/2): PERCENT and FLAT on a product or a category (or its parent).
 * When several apply, the best one for the customer wins. Buy-X-Get-Y and bill-value offers are
 * not in the prototype.
 */
@Service
public class OfferService {

    private final OfferRepository offers;

    public OfferService(OfferRepository offers) {
        this.offers = offers;
    }

    public record AppliedOffer(Long offerId, String name, BigDecimal unitDiscount) {}

    public List<Offer> running(LocalDateTime now) {
        return offers.findRunning(now);
    }

    public Optional<AppliedOffer> best(List<Offer> running, Product p, BigDecimal unitPrice) {
        AppliedOffer best = null;
        for (Offer o : running) {
            if (!appliesTo(o, p)) {
                continue;
            }
            BigDecimal disc = switch (o.getType()) {
                case PERCENT -> Money.percent(unitPrice, o.getValue());
                case FLAT -> o.getValue().min(unitPrice);
                default -> BigDecimal.ZERO;
            };
            if (disc.signum() > 0 && (best == null || disc.compareTo(best.unitDiscount()) > 0)) {
                best = new AppliedOffer(o.getId(), o.getName(), Money.round(disc));
            }
        }
        return Optional.ofNullable(best);
    }

    private boolean appliesTo(Offer o, Product p) {
        if (o.getProductId() != null) {
            return o.getProductId().equals(p.getId());
        }
        if (o.getCategoryId() != null) {
            return o.getCategoryId().equals(p.getCategory().getId())
                    || o.getCategoryId().equals(p.getCategory().getParentId());
        }
        return true; // shop-wide offer
    }
}
