package com.vertex.csms.product;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vertex.csms.common.ApiException;
import com.vertex.csms.inventory.MovementType;
import com.vertex.csms.inventory.StockService;
import com.vertex.csms.security.CurrentUser;

@Service
public class ProductService {

    private final ProductRepository products;
    private final VariantRepository variants;
    private final CategoryRepository categories;
    private final StockService stock;

    public ProductService(ProductRepository products, VariantRepository variants,
                          CategoryRepository categories, StockService stock) {
        this.products = products;
        this.variants = variants;
        this.categories = categories;
        this.stock = stock;
    }

    public record ProductDetail(Long id, String name, String brand, String fabric, Gender gender, String hsnCode,
                                TaxClass taxClass, boolean returnable, boolean active, Long categoryId,
                                String categoryName, List<VariantDto> variants) {}

    @Transactional(readOnly = true)
    public ProductDetail get(Long id) {
        Product p = products.findById(id).orElseThrow(() -> ApiException.notFound("ERR-PRD-004", "Product not found."));
        return toDetail(p);
    }

    /** Create a product and all its size x colour variants in one step (REQ-PRD-1/2/3). */
    @Transactional
    public ProductDetail create(ProductRequest req) {
        Product p = new Product();
        apply(p, req);
        products.save(p);
        saveVariants(p, req);
        return toDetail(p);
    }

    @Transactional
    public ProductDetail update(Long id, ProductRequest req) {
        Product p = products.findById(id).orElseThrow(() -> ApiException.notFound("ERR-PRD-004", "Product not found."));
        apply(p, req);
        saveVariants(p, req);
        return toDetail(p);
    }

    private void apply(Product p, ProductRequest req) {
        Category cat = categories.findById(req.categoryId())
                .orElseThrow(() -> ApiException.badRequest("ERR-VAL-001", "Category not found", "categoryId"));
        p.setCategory(cat);
        p.setName(req.name().trim());
        p.setBrand(blankToNull(req.brand()));
        p.setFabric(blankToNull(req.fabric()));
        p.setGender(req.gender());
        p.setHsnCode(req.hsnCode());
        p.setTaxClass(req.taxClass());
        p.setReturnable(req.returnable());
        p.setActive(req.active() == null || req.active());
    }

    private void saveVariants(Product p, ProductRequest req) {
        Long userId = CurrentUser.id();
        boolean clearance = req.clearanceReason() != null && req.clearanceReason().trim().length() >= 3;

        // Validate the whole grid first so nothing is half-saved.
        Set<String> combos = new HashSet<>();
        for (Variant existing : p.getVariants()) {
            combos.add(key(existing.getSize(), existing.getColour()));
        }
        Map<Long, Variant> existingById = new HashMap<>();
        p.getVariants().forEach(v -> existingById.put(v.getId(), v));
        Set<String> seenInRequest = new HashSet<>();
        for (ProductRequest.VariantRow row : req.variants()) {
            String k = key(row.size(), row.colour());
            if (!seenInRequest.add(k) || (row.id() == null && combos.contains(k))) {
                throw ApiException.conflict("ERR-PRD-002", "Size " + row.size() + " / colour " + row.colour()
                        + " appears twice.", "variants");
            }
            if (row.sellingPrice().compareTo(row.mrp()) > 0) {
                throw ApiException.badRequest("ERR-VAL-001", "Selling price cannot be more than MRP ("
                        + row.size() + "/" + row.colour() + ").", "sellingPrice");
            }
            if (row.sellingPrice().compareTo(row.costPrice()) < 0 && !clearance) {
                throw ApiException.badRequest("ERR-PRD-003", "Selling price is below cost for " + row.size() + "/"
                        + row.colour() + ". Tick \"Clearance price\" and give a reason to allow it.", "sellingPrice");
            }
            if (row.id() != null && !existingById.containsKey(row.id())) {
                throw ApiException.badRequest("ERR-VAL-001", "Variant " + row.id() + " does not belong to this product.", "variants");
            }
        }

        String lastBarcode = variants.maxInStoreBarcode();
        for (ProductRequest.VariantRow row : req.variants()) {
            int reorder = row.reorderLevel() == null ? 2 : row.reorderLevel();
            if (row.id() != null) {
                Variant v = existingById.get(row.id());
                v.setCostPrice(row.costPrice());
                v.setSellingPrice(row.sellingPrice());
                v.setMrp(row.mrp());
                v.setReorderLevel(reorder);
                continue;
            }
            Variant v = new Variant();
            v.setProduct(p);
            v.setSize(row.size().trim());
            v.setColour(row.colour().trim());
            v.setCostPrice(row.costPrice());
            v.setSellingPrice(row.sellingPrice());
            v.setMrp(row.mrp());
            v.setReorderLevel(reorder);
            v.setSku(makeSku(p, v));
            if (variants.existsBySku(v.getSku())) {
                throw ApiException.conflict("ERR-PRD-002", "SKU " + v.getSku() + " already exists.", "sku");
            }
            if (row.barcode() != null && !row.barcode().isBlank()) {
                if (variants.existsByBarcode(row.barcode())) {
                    throw ApiException.conflict("ERR-PRD-002", "Barcode " + row.barcode() + " is already used.", "barcode");
                }
                v.setBarcode(row.barcode());
            } else {
                lastBarcode = Ean13.next(lastBarcode);
                v.setBarcode(lastBarcode);
            }
            variants.save(v);
            p.getVariants().add(v);
            int opening = row.openingStock() == null ? 0 : row.openingStock();
            stock.change(v, MovementType.OPENING, opening, "PRODUCT", p.getId(), "Opening stock", userId);
        }
    }

    /** Same pattern as the seed data: KUR-0001-M-BLU (category prefix, product id, size, colour). */
    private String makeSku(Product p, Variant v) {
        String prefix = p.getCategory().getName().replaceAll("[^A-Za-z]", "").toUpperCase(Locale.ROOT);
        prefix = (prefix + "XXX").substring(0, 3);
        String colour = v.getColour().replaceAll("[^A-Za-z]", "").toUpperCase(Locale.ROOT);
        colour = (colour + "XXX").substring(0, 3);
        String size = v.getSize().replaceAll("\\s+", "");
        return String.format("%s-%04d-%s-%s", prefix, p.getId(), size, colour);
    }

    private ProductDetail toDetail(Product p) {
        boolean showCost = CurrentUser.isOwner();
        return new ProductDetail(p.getId(), p.getName(), p.getBrand(), p.getFabric(), p.getGender(), p.getHsnCode(),
                p.getTaxClass(), p.isReturnable(), p.isActive(), p.getCategory().getId(), p.getCategory().getName(),
                p.getVariants().stream().map(v -> VariantDto.of(v, showCost)).toList());
    }

    private static String key(String size, String colour) {
        return size.trim().toLowerCase(Locale.ROOT) + "|" + colour.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
