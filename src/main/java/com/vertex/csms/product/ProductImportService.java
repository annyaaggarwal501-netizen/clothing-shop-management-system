package com.vertex.csms.product;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.apache.poi.ss.usermodel.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.vertex.csms.billing.TaxCalculator;
import com.vertex.csms.common.ApiException;

/**
 * Bulk product import from .csv or .xlsx (REQ-PRD-5, SRS 10.3.1). Two steps:
 *   preview: every row is checked and marked VALID / WARNING / INVALID; nothing is saved.
 *   commit:  the same file is checked again and the VALID + WARNING rows are saved in one transaction.
 * Rows of the same product (same name + category) become one product with several variants. A product
 * that already exists gets the new sizes/colours added to it.
 */
@Service
public class ProductImportService {

    static final long MAX_BYTES = 5L * 1024 * 1024;
    static final List<String> REQUIRED = List.of("product_name", "category", "hsn_code", "tax_class", "size", "colour",
            "cost_price", "selling_price", "mrp");
    private static final Set<String> NON_RETURNABLE_CATEGORIES = Set.of("innerwear", "blouses", "nightwear", "infant wear");
    private static final List<TaxCalculator.Slab> APPAREL_SLABS = List.of(
            new TaxCalculator.Slab(BigDecimal.ZERO, new BigDecimal("2500.00"), new BigDecimal("5.00")),
            new TaxCalculator.Slab(new BigDecimal("2500.01"), null, new BigDecimal("18.00")));

    private final JdbcTemplate jdbc;
    private final ProductService productService;

    public ProductImportService(JdbcTemplate jdbc, ProductService productService) {
        this.jdbc = jdbc;
        this.productService = productService;
    }

    public record RowResult(int rowNo, String status, String productName, String category, String size, String colour,
                            BigDecimal sellingPrice, Integer openingStock, boolean newProduct, List<String> messages) {}

    public record Preview(String fileName, int totalRows, int valid, int warnings, int invalid, int newProducts,
                          int existingProducts, int newVariants, List<RowResult> rows) {}

    public record CommitResult(int productsCreated, int productsUpdated, int variantsCreated, int openingPieces,
                               int rowsSkipped) {}

    /** One checked row, with the values needed to save it. */
    private static final class Row {
        RowResult result;
        String groupKey;
        Long categoryId;
        String categoryName;
        Gender gender;
        String name, brand, fabric, hsn, size, colour, barcode;
        TaxClass taxClass;
        boolean returnable;
        BigDecimal cost, selling, mrp;
        int opening;
        Long existingProductId;
    }

    public Preview preview(MultipartFile file) {
        List<Row> rows = check(file);
        Set<String> newGroups = new HashSet<>(), oldGroups = new HashSet<>();
        int valid = 0, warn = 0, invalid = 0;
        for (Row r : rows) {
            switch (r.result.status()) {
                case "VALID" -> valid++;
                case "WARNING" -> warn++;
                default -> invalid++;
            }
            if (!r.result.status().equals("INVALID")) {
                (r.existingProductId == null ? newGroups : oldGroups).add(r.groupKey);
            }
        }
        return new Preview(file.getOriginalFilename(), rows.size(), valid, warn, invalid, newGroups.size(),
                oldGroups.size(), valid + warn, rows.stream().map(r -> r.result).toList());
    }

    @Transactional
    public CommitResult commit(MultipartFile file) {
        List<Row> rows = check(file);
        Map<String, List<Row>> groups = new LinkedHashMap<>();
        int skipped = 0;
        for (Row r : rows) {
            if (r.result.status().equals("INVALID")) {
                skipped++;
            } else {
                groups.computeIfAbsent(r.groupKey, k -> new ArrayList<>()).add(r);
            }
        }
        if (groups.isEmpty()) {
            throw ApiException.badRequest("ERR-PRD-001", "The file has no valid rows to import.");
        }
        int created = 0, updated = 0, variants = 0, pieces = 0;
        for (List<Row> g : groups.values()) {
            Row first = g.get(0);
            List<ProductRequest.VariantRow> vrows = g.stream().map(r -> new ProductRequest.VariantRow(null, r.size, r.colour,
                    r.cost, r.selling, r.mrp, r.opening, 2, r.barcode)).toList();
            if (first.existingProductId == null) {
                productService.create(new ProductRequest(first.categoryId, first.name, first.brand, first.fabric,
                        first.gender, first.hsn, first.taxClass, first.returnable, true, null, vrows));
                created++;
            } else {
                // Keep the existing product's details; only add the new variants.
                ProductService.ProductDetail p = productService.get(first.existingProductId);
                productService.update(p.id(), new ProductRequest(p.categoryId(), p.name(), p.brand(), p.fabric(),
                        p.gender(), p.hsnCode(), p.taxClass(), p.returnable(), p.active(), null, vrows));
                updated++;
            }
            variants += g.size();
            pieces += g.stream().mapToInt(r -> r.opening).sum();
        }
        return new CommitResult(created, updated, variants, pieces, skipped);
    }

    // ---------------------------------------------------------------------------------------------

    private List<Row> check(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("ERR-PRD-001", "The file is empty.", "file");
        }
        if (file.getSize() > MAX_BYTES) {
            throw ApiException.badRequest("ERR-PRD-001", "The file is larger than 5 MB.", "file");
        }
        String name = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
        List<List<String>> table;
        try {
            if (name.endsWith(".csv")) {
                table = readCsv(new String(file.getBytes(), StandardCharsets.UTF_8));
            } else if (name.endsWith(".xlsx")) {
                table = readXlsx(file.getBytes());
            } else {
                throw ApiException.badRequest("ERR-PRD-005", "Unsupported file type. Upload a .csv or .xlsx file.", "file");
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof ApiException ae) throw ae;
            throw ApiException.badRequest("ERR-PRD-005", "The file could not be read: " + e.getMessage(), "file");
        }
        if (table.isEmpty()) {
            throw ApiException.badRequest("ERR-PRD-001", "The file has no header row.", "file");
        }

        Map<String, Integer> col = new HashMap<>();
        List<String> header = table.get(0);
        for (int i = 0; i < header.size(); i++) {
            col.put(header.get(i).trim().toLowerCase(Locale.ROOT).replace(' ', '_'), i);
        }
        List<String> missing = REQUIRED.stream().filter(c -> !col.containsKey(c)).toList();
        if (!missing.isEmpty()) {
            throw ApiException.badRequest("ERR-PRD-001", "Missing column(s): " + String.join(", ", missing)
                    + ". Use the template in db/products_import.csv.", "file");
        }
        List<List<String>> data = table.subList(1, table.size()).stream()
                .filter(r -> r.stream().anyMatch(c -> !c.isBlank())).toList();
        if (data.isEmpty()) {
            throw ApiException.badRequest("ERR-PRD-001", "The file has a header but no data rows.", "file");
        }
        if (data.size() > 5000) {
            throw ApiException.badRequest("ERR-PRD-001", "At most 5,000 rows per file.", "file");
        }

        // Load what we compare against once, instead of one query per row.
        Categories cats = new Categories(jdbc.queryForList(
                "SELECT c.category_id, c.name, c.parent_id, p.name AS parent FROM category c "
                        + "LEFT JOIN category p ON p.category_id = c.parent_id WHERE c.is_active = TRUE"));
        Set<String> dbBarcodes = new HashSet<>(jdbc.queryForList("SELECT barcode FROM variant", String.class));
        Map<String, Long> dbProducts = new HashMap<>();
        jdbc.query("SELECT product_id, name, category_id FROM product", rs -> {
            dbProducts.put(rs.getString("name").trim().toLowerCase(Locale.ROOT) + "|" + rs.getLong("category_id"),
                    rs.getLong("product_id"));
        });
        Set<String> dbCombos = new HashSet<>();
        jdbc.query("SELECT product_id, size, colour FROM variant", rs -> {
            dbCombos.add(rs.getLong("product_id") + "|" + rs.getString("size").toLowerCase(Locale.ROOT) + "|"
                    + rs.getString("colour").toLowerCase(Locale.ROOT));
        });

        Set<String> fileBarcodes = new HashSet<>();
        Set<String> fileCombos = new HashSet<>();
        Map<String, Row> groupFirst = new HashMap<>();
        List<Row> out = new ArrayList<>();

        for (int i = 0; i < data.size(); i++) {
            List<String> cells = data.get(i);
            Row r = new Row();
            List<String> errors = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            java.util.function.Function<String, String> get = c -> {
                Integer idx = col.get(c);
                return idx == null || idx >= cells.size() ? "" : cells.get(idx).trim();
            };

            r.name = get.apply("product_name").replaceAll("\\s+", " ");
            r.brand = blank(get.apply("brand"));
            r.fabric = blank(get.apply("fabric"));
            r.size = get.apply("size");
            r.colour = get.apply("colour");
            r.hsn = get.apply("hsn_code");
            r.barcode = blank(get.apply("barcode"));
            if (r.barcode != null && r.barcode.matches("\\d+\\.0+")) r.barcode = r.barcode.replaceAll("\\.0+$", "");

            if (r.name.length() < 2 || r.name.length() > 100) errors.add("ERR-VAL-001 product_name must be 2-100 characters");
            if (r.size.isEmpty() || r.size.length() > 10) errors.add("ERR-VAL-001 size is required (max 10 characters)");
            if (r.colour.isEmpty() || r.colour.length() > 30) errors.add("ERR-VAL-001 colour is required (max 30 characters)");
            if (!r.hsn.matches("^[0-9]{4,8}$")) errors.add("ERR-VAL-001 hsn_code must be 4-8 digits");

            // Category: "Kurtis" or "Men > Winterwear" when the name is used under more than one parent.
            String catText = get.apply("category");
            List<Map<String, Object>> found = cats.find(catText);
            if (found.isEmpty()) {
                errors.add("ERR-VAL-001 unknown category \"" + catText + "\"");
            } else {
                Map<String, Object> c = found.get(0);
                r.categoryId = ((Number) c.get("category_id")).longValue();
                r.categoryName = (String) c.get("name");
                String root = c.get("parent") != null ? (String) c.get("parent") : (String) c.get("name");
                if (found.size() > 1) {
                    warnings.add("Category \"" + catText + "\" exists under " + found.stream().map(x -> String.valueOf(x.get("parent")))
                            .toList() + "; used " + root + ". Write it as \"Parent > " + r.categoryName + "\", e.g. \"Kids > "
                            + r.categoryName + "\", to choose.");
                }
                r.gender = switch (root.toLowerCase(Locale.ROOT)) {
                    case "women" -> Gender.WOMEN;
                    case "men" -> Gender.MEN;
                    case "kids" -> Gender.KIDS;
                    default -> Gender.UNISEX;
                };
                r.returnable = !NON_RETURNABLE_CATEGORIES.contains(r.categoryName.toLowerCase(Locale.ROOT));
            }
            String g = get.apply("gender").toUpperCase(Locale.ROOT);
            if (!g.isEmpty()) {
                try { r.gender = Gender.valueOf(g); } catch (IllegalArgumentException e) { errors.add("ERR-VAL-001 gender must be MEN, WOMEN, KIDS or UNISEX"); }
            }
            String ret = get.apply("returnable").toLowerCase(Locale.ROOT);
            if (!ret.isEmpty()) r.returnable = ret.startsWith("t") || ret.startsWith("y") || ret.equals("1");

            try {
                r.taxClass = TaxClass.valueOf(get.apply("tax_class").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                errors.add("ERR-VAL-001 tax_class must be APPAREL or FABRIC");
            }
            if (r.taxClass == TaxClass.APPAREL && r.hsn.matches("^5[0-5].*")) {
                warnings.add("HSN " + r.hsn + " is a fabric chapter (50-55) but tax_class is APPAREL");
            }
            if (r.taxClass == TaxClass.FABRIC && r.hsn.matches("^6[12].*")) {
                warnings.add("HSN " + r.hsn + " is a garment chapter (61-62) but tax_class is FABRIC");
            }

            r.cost = money(get.apply("cost_price"), "cost_price", errors);
            r.selling = money(get.apply("selling_price"), "selling_price", errors);
            r.mrp = money(get.apply("mrp"), "mrp", errors);
            if (r.selling != null && r.selling.signum() <= 0) errors.add("ERR-VAL-001 selling_price must be more than 0");
            if (r.selling != null && r.mrp != null && r.selling.compareTo(r.mrp) > 0) errors.add("ERR-VAL-001 selling_price is above mrp");
            if (r.selling != null && r.cost != null && r.selling.compareTo(r.cost) < 0) {
                errors.add("ERR-PRD-003 selling_price is below cost_price (set clearance prices on the product screen)");
            }
            if (r.taxClass == TaxClass.APPAREL && r.selling != null && TaxCalculator.isSlabSensitive(APPAREL_SLABS, r.selling)) {
                warnings.add("WARN-GST-001 price " + r.selling + " is in the band where a small discount changes GST 18% -> 5%");
            }
            String os = get.apply("opening_stock").replaceAll("\\.0+$", "");
            if (os.isEmpty()) {
                r.opening = 0;
            } else if (os.matches("\\d{1,5}")) {
                r.opening = Integer.parseInt(os);
            } else {
                errors.add("ERR-VAL-001 opening_stock must be a whole number 0-99999");
            }

            if (r.barcode != null) {
                if (!r.barcode.matches("^[0-9]{8,20}$")) errors.add("ERR-VAL-001 barcode must be 8-20 digits");
                else if (dbBarcodes.contains(r.barcode)) errors.add("ERR-PRD-002 barcode " + r.barcode + " already exists");
                else if (!fileBarcodes.add(r.barcode)) errors.add("ERR-PRD-002 barcode " + r.barcode + " appears twice in the file");
            }

            if (r.categoryId != null) {
                r.groupKey = r.name.toLowerCase(Locale.ROOT) + "|" + r.categoryId;
                r.existingProductId = dbProducts.get(r.groupKey);
                String combo = r.size.toLowerCase(Locale.ROOT) + "|" + r.colour.toLowerCase(Locale.ROOT);
                if (r.existingProductId != null && dbCombos.contains(r.existingProductId + "|" + combo)) {
                    errors.add("ERR-PRD-002 " + r.name + " " + r.size + "/" + r.colour + " already exists");
                } else if (!fileCombos.add(r.groupKey + "|" + combo)) {
                    errors.add("ERR-PRD-002 " + r.size + "/" + r.colour + " appears twice for this product in the file");
                }
                Row first = groupFirst.putIfAbsent(r.groupKey, r);
                if (first != null && r.existingProductId == null
                        && (!Objects.equals(first.hsn, r.hsn) || first.taxClass != r.taxClass || !Objects.equals(first.brand, r.brand))) {
                    warnings.add("Brand / HSN / tax class differ from row " + first.result.rowNo() + "; that row's values are used");
                }
            }

            String status = !errors.isEmpty() ? "INVALID" : !warnings.isEmpty() ? "WARNING" : "VALID";
            List<String> msgs = new ArrayList<>(errors);
            msgs.addAll(warnings);
            r.result = new RowResult(i + 2, status, r.name, catText, r.size, r.colour, r.selling, r.opening,
                    r.categoryId != null && r.existingProductId == null, msgs);
            out.add(r);
        }
        return out;
    }

    /** Category lookup by "Name" or "Parent > Name", case-insensitive. */
    private static final class Categories {
        private final List<Map<String, Object>> all;

        Categories(List<Map<String, Object>> all) { this.all = all; }

        List<Map<String, Object>> find(String text) {
            String t = text.trim().toLowerCase(Locale.ROOT);
            if (t.contains(">")) {
                String[] parts = t.split(">", 2);
                return all.stream().filter(c -> String.valueOf(c.get("name")).equalsIgnoreCase(parts[1].trim())
                        && String.valueOf(c.get("parent")).equalsIgnoreCase(parts[0].trim())).toList();
            }
            return all.stream().filter(c -> String.valueOf(c.get("name")).equalsIgnoreCase(t))
                    .sorted(Comparator.comparing(c -> ((Number) c.get("category_id")).longValue())).toList();
        }
    }

    private static BigDecimal money(String v, String field, List<String> errors) {
        try {
            BigDecimal b = new BigDecimal(v.replace(",", "").replace("₹", "").trim());
            if (b.signum() < 0 || b.scale() > 2) throw new NumberFormatException();
            return b.setScale(2);
        } catch (NumberFormatException e) {
            errors.add("ERR-VAL-001 " + field + " must be an amount like 1050 or 1050.00");
            return null;
        }
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** Small CSV reader: commas, "quoted, values", "" escapes, CRLF or LF, optional UTF-8 BOM. */
    static List<List<String>> readCsv(String text) {
        if (text.startsWith("﻿")) text = text.substring(1);
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') { cell.append('"'); i++; }
                else if (ch == '"') quoted = false;
                else cell.append(ch);
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                row.add(cell.toString()); cell.setLength(0);
            } else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(cell.toString()); cell.setLength(0);
                rows.add(row); row = new ArrayList<>();
            } else {
                cell.append(ch);
            }
        }
        if (cell.length() > 0 || !row.isEmpty()) { row.add(cell.toString()); rows.add(row); }
        return rows;
    }

    private static List<List<String>> readXlsx(byte[] bytes) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        DataFormatter fmt = new DataFormatter(Locale.ROOT);
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheetAt(0);
            for (org.apache.poi.ss.usermodel.Row r : sheet) {
                List<String> cells = new ArrayList<>();
                for (int c = 0; c < Math.max(r.getLastCellNum(), 0); c++) {
                    Cell cell = r.getCell(c, org.apache.poi.ss.usermodel.Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    if (cell == null) {
                        cells.add("");
                    } else if (cell.getCellType() == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell)) {
                        // Plain digits, so a 13-digit barcode does not turn into 2E+12.
                        cells.add(BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString());
                    } else {
                        cells.add(fmt.formatCellValue(cell));
                    }
                }
                rows.add(cells);
            }
        }
        return rows;
    }
}
