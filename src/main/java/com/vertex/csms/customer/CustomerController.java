package com.vertex.csms.customer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import com.vertex.csms.common.ApiException;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService service;
    private final CustomerRepository customers;
    private final JdbcTemplate jdbc;

    public CustomerController(CustomerService service, CustomerRepository customers, JdbcTemplate jdbc) {
        this.service = service;
        this.customers = customers;
        this.jdbc = jdbc;
    }

    /** Lookup by mobile at the POS: GET /api/v1/customers/lookup?mobile=98xxxxxxxx */
    @GetMapping("/lookup")
    public Customer byMobile(@RequestParam String mobile) {
        String m = CustomerService.normaliseMobile(mobile);
        CustomerService.checkMobile(m);
        return customers.findByMobile(m).orElseThrow(() -> ApiException.notFound("INFO-CUS-001",
                "No customer with mobile " + m + ". You can register them now."));
    }

    /**
     * Customer list with last visit, total spend and visit count (REQ-CUS-3).
     * ?mobile= gives an exact match (same as /lookup but as a list); ?q= searches name, mobile or area.
     */
    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(required = false) String q,
                                          @RequestParam(required = false) String mobile) {
        StringBuilder sql = new StringBuilder("""
                SELECT c.customer_id, c.name, c.mobile, c.area, c.birthday, c.wa_opt_in, c.consent_method,
                       c.loyalty_points, c.credit_limit, c.credit_due, c.is_active,
                       COUNT(s.sale_id) AS visits, COALESCE(SUM(s.grand_total), 0) AS total_spend,
                       MAX(s.completed_at) AS last_visit
                FROM customer c
                LEFT JOIN sale s ON s.customer_id = c.customer_id
                     AND s.status IN ('COMPLETED','PARTIALLY_RETURNED','RETURNED')
                WHERE 1 = 1""");
        List<Object> args = new ArrayList<>();
        if (mobile != null && !mobile.isBlank()) {
            sql.append(" AND c.mobile = ?");
            args.add(CustomerService.normaliseMobile(mobile));
        }
        if (q != null && !q.isBlank()) {
            sql.append(" AND (c.name LIKE ? OR c.mobile LIKE ? OR c.area LIKE ?)");
            String like = "%" + q.trim() + "%";
            args.addAll(List.of(like, like, like));
        }
        sql.append(" GROUP BY c.customer_id ORDER BY c.wa_opt_in DESC, last_visit DESC, c.name LIMIT 200");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    @GetMapping("/{id}")
    public Customer get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public Customer create(@RequestBody CustomerService.CustomerRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public Customer update(@PathVariable Long id, @RequestBody CustomerService.CustomerRequest req) {
        return service.update(id, req);
    }

    @PutMapping("/{id}/consent")
    public Customer consent(@PathVariable Long id, @RequestBody CustomerService.ConsentRequest req) {
        return service.setConsent(id, req);
    }

    @GetMapping("/{id}/history")
    public List<Map<String, Object>> history(@PathVariable Long id) {
        return jdbc.queryForList("""
                SELECT s.sale_id, s.invoice_no, s.status, s.completed_at, s.grand_total,
                       (SELECT SUM(quantity) FROM sale_line sl WHERE sl.sale_id = s.sale_id) AS items
                FROM sale s WHERE s.customer_id = ? AND s.status NOT IN ('DRAFT', 'HELD')
                ORDER BY s.sale_id DESC LIMIT 50""", id);
    }
}
