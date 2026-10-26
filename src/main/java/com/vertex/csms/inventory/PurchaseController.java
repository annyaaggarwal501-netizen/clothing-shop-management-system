package com.vertex.csms.inventory;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

/** Stock-in. Owner only (SecurityConfig: /api/v1/purchases/**, /api/v1/suppliers/**). */
@RestController
@RequestMapping("/api/v1")
public class PurchaseController {

    private final PurchaseService service;
    private final SupplierRepository suppliers;
    private final JdbcTemplate jdbc;

    public PurchaseController(PurchaseService service, SupplierRepository suppliers, JdbcTemplate jdbc) {
        this.service = service;
        this.suppliers = suppliers;
        this.jdbc = jdbc;
    }

    @PostMapping("/purchases")
    public PurchaseService.PurchaseResult create(@RequestBody @Valid PurchaseService.PurchaseRequest req) {
        return service.record(req);
    }

    @GetMapping("/purchases")
    public List<Map<String, Object>> recent() {
        return jdbc.queryForList("""
                SELECT pu.purchase_id, pu.bill_no, pu.bill_date, s.name AS supplier, pu.created_at,
                       COUNT(pl.pline_id) AS line_count, SUM(pl.quantity) AS pieces,
                       SUM(pl.quantity * pl.unit_cost) AS bill_value
                FROM purchase pu
                JOIN supplier s ON s.supplier_id = pu.supplier_id
                JOIN purchase_line pl ON pl.purchase_id = pu.purchase_id
                GROUP BY pu.purchase_id, pu.bill_no, pu.bill_date, s.name, pu.created_at
                ORDER BY pu.purchase_id DESC LIMIT 20""");
    }

    @GetMapping("/suppliers")
    public List<Supplier> suppliers() {
        return suppliers.findAll();
    }
}
