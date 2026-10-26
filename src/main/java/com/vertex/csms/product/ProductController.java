package com.vertex.csms.product;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;


import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
public class ProductController {

    private final ProductService service;
    private final ProductImportService importer;
    private final JdbcTemplate jdbc;

    public ProductController(ProductService service, ProductImportService importer, JdbcTemplate jdbc) {
        this.service = service;
        this.importer = importer;
        this.jdbc = jdbc;
    }

    /** Bulk import, step 1: check every row and show VALID / WARNING / INVALID. Nothing is saved. */
    @PostMapping(value = "/products/import/preview", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('OWNER')")
    public ProductImportService.Preview importPreview(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        return importer.preview(file);
    }

    /** Bulk import, step 2: save the VALID and WARNING rows of the same file in one transaction. */
    @PostMapping(value = "/products/import/commit", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('OWNER')")
    public ProductImportService.CommitResult importCommit(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        return importer.commit(file);
    }

    /** Product list and search (REQ-PRD-6): name, brand, SKU or barcode. Owner and Staff. */
    @GetMapping("/products")
    public List<Map<String, Object>> list(@RequestParam(required = false) String q,
                                          @RequestParam(required = false) Long categoryId,
                                          @RequestParam(defaultValue = "false") boolean includeInactive) {
        StringBuilder sql = new StringBuilder("""
                SELECT p.product_id, p.name, p.brand, p.hsn_code, p.tax_class, p.gender, p.is_returnable,
                       p.is_active, c.name AS category, pc.name AS parent_category,
                       COUNT(v.variant_id) AS variant_count, COALESCE(SUM(v.stock_qty), 0) AS total_stock,
                       MIN(v.selling_price) AS min_price, MAX(v.selling_price) AS max_price,
                       SUM(v.stock_qty <= v.reorder_level) AS low_variants
                FROM product p
                JOIN category c ON c.category_id = p.category_id
                LEFT JOIN category pc ON pc.category_id = c.parent_id
                LEFT JOIN variant v ON v.product_id = p.product_id
                WHERE 1 = 1""");
        List<Object> args = new ArrayList<>();
        if (!includeInactive) {
            sql.append(" AND p.is_active = TRUE");
        }
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim() + "%";
            sql.append(" AND (p.name LIKE ? OR p.brand LIKE ? OR c.name LIKE ? OR EXISTS (SELECT 1 FROM variant v2 "
                    + "WHERE v2.product_id = p.product_id AND (v2.sku LIKE ? OR v2.barcode = ?)))");
            args.addAll(List.of(like, like, like, like, q.trim()));
        }
        if (categoryId != null) {
            sql.append(" AND (c.category_id = ? OR c.parent_id = ?)");
            args.add(categoryId);
            args.add(categoryId);
        }
        sql.append(" GROUP BY p.product_id, p.name, p.brand, p.hsn_code, p.tax_class, p.gender, p.is_returnable,"
                + " p.is_active, c.name, pc.name ORDER BY p.name LIMIT 200");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    @GetMapping("/products/{id}")
    public ProductService.ProductDetail get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/products")
    @PreAuthorize("hasRole('OWNER')")
    public ProductService.ProductDetail create(@RequestBody @Valid ProductRequest req) {
        return service.create(req);
    }

    @PutMapping("/products/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ProductService.ProductDetail update(@PathVariable Long id, @RequestBody @Valid ProductRequest req) {
        return service.update(id, req);
    }

    @GetMapping("/categories")
    public List<Map<String, Object>> categories() {
        return jdbc.queryForList("""
                SELECT c.category_id, c.name, c.parent_id, p.name AS parent_name,
                       CONCAT(COALESCE(CONCAT(p.name, ' > '), ''), c.name) AS label
                FROM category c LEFT JOIN category p ON p.category_id = c.parent_id
                WHERE c.is_active = TRUE ORDER BY label""");
    }

}
