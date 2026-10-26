package com.vertex.csms.messaging;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.vertex.csms.billing.ReceiptService;

@RestController
@RequestMapping("/api/v1")
public class WhatsAppController {

    private final WhatsAppService service;
    private final BillLinkService links;
    private final ReceiptService receipts;
    private final JdbcTemplate jdbc;
    private final String publicBaseUrl;

    public WhatsAppController(WhatsAppService service, BillLinkService links, ReceiptService receipts,
                              JdbcTemplate jdbc, @Value("${csms.public-base-url:}") String publicBaseUrl) {
        this.service = service;
        this.links = links;
        this.receipts = receipts;
        this.jdbc = jdbc;
        this.publicBaseUrl = publicBaseUrl;
    }

    @PostMapping("/whatsapp/ebill/{saleId}")
    public WhatsAppService.ShareLink ebill(@PathVariable Long saleId) {
        return service.ebill(saleId, baseUrl());
    }

    @GetMapping("/whatsapp/product/{productId}")
    public WhatsAppService.ProductShare product(@PathVariable Long productId, @RequestParam(required = false) Long customerId) {
        return service.product(productId, customerId);
    }

    /** Called when staff actually open the link for a customer, so the message is logged. */
    @PostMapping("/whatsapp/product/{productId}/sent")
    public void productSent(@PathVariable Long productId, @RequestParam Long customerId) {
        service.logProductShare(customerId);
    }

    @GetMapping("/whatsapp/log")
    public List<Map<String, Object>> recent() {
        return jdbc.queryForList("""
                SELECT m.message_id, m.purpose, m.mode, m.status, m.created_at, c.name, c.mobile
                FROM wa_message m JOIN customer c ON c.customer_id = m.customer_id
                ORDER BY m.message_id DESC LIMIT 30""");
    }

    /** The e-bill page opened from WhatsApp. No login needed, but the link must carry a valid signature. */
    @GetMapping("/public/ebill/{token}")
    public ReceiptService.Receipt publicBill(@PathVariable String token) {
        return receipts.bySaleId(links.saleId(token), true);
    }

    private String baseUrl() {
        if (publicBaseUrl != null && !publicBaseUrl.isBlank()) {
            return publicBaseUrl.replaceAll("/+$", "");
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }
}
