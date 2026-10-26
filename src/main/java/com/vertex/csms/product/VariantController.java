package com.vertex.csms.product;

import java.util.List;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.vertex.csms.common.ApiException;
import com.vertex.csms.security.CurrentUser;

import jakarta.persistence.EntityManager;

@RestController
@RequestMapping("/api/v1/variants")
public class VariantController {

    private final VariantRepository variants;
    private final EntityManager em;

    public VariantController(VariantRepository variants, EntityManager em) {
        this.variants = variants;
        this.em = em;
    }

    /** Barcode scan: GET /api/v1/variants?barcode=2000000000015 (also accepts ?sku=). */
    @GetMapping
    @Transactional(readOnly = true)
    public VariantDto byCode(@RequestParam(required = false) String barcode, @RequestParam(required = false) String sku) {
        String code = barcode != null ? barcode.trim() : (sku != null ? sku.trim() : "");
        Variant v = variants.findByBarcode(code).or(() -> variants.findBySku(code))
                .filter(x -> x.getProduct().isActive())
                .orElseThrow(() -> ApiException.notFound("ERR-PRD-004",
                        "No item found for \"" + code + "\". Check the barcode or search by name."));
        return VariantDto.of(v, CurrentUser.isOwner());
    }

    /** Search variants by product name, SKU, size or colour (for the POS and stock-in screens). */
    @GetMapping("/search")
    @Transactional(readOnly = true)
    public List<VariantDto> search(@RequestParam String q) {
        String like = "%" + q.trim().toLowerCase() + "%";
        List<Variant> found = em.createQuery("""
                select v from Variant v join fetch v.product p join fetch p.category
                where p.isActive = true and (lower(p.name) like :q or lower(v.sku) like :q
                      or lower(concat(p.name, ' ', v.size, ' ', v.colour)) like :q or v.barcode = :exact)
                order by p.name, v.id""", Variant.class)
                .setParameter("q", like).setParameter("exact", q.trim())
                .setMaxResults(30).getResultList();
        boolean showCost = CurrentUser.isOwner();
        return found.stream().map(v -> VariantDto.of(v, showCost)).toList();
    }

    /** Low-stock list: stock_qty <= reorder_level (week-1 query). */
    @GetMapping("/low-stock")
    @Transactional(readOnly = true)
    public List<VariantDto> lowStock() {
        List<Variant> found = em.createQuery("""
                select v from Variant v join fetch v.product p join fetch p.category
                where p.isActive = true and v.stockQty <= v.reorderLevel
                order by v.stockQty, p.name""", Variant.class).getResultList();
        boolean showCost = CurrentUser.isOwner();
        return found.stream().map(v -> VariantDto.of(v, showCost)).toList();
    }
}
