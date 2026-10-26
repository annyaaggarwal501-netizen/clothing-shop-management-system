package com.vertex.csms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import com.vertex.csms.billing.BillingService;
import com.vertex.csms.billing.BillingService.CartLine;
import com.vertex.csms.billing.BillingService.CompleteRequest;
import com.vertex.csms.billing.BillingService.PaymentRequest;
import com.vertex.csms.billing.HeldBillService;
import com.vertex.csms.billing.PaymentMode;
import com.vertex.csms.billing.ReceiptService;
import com.vertex.csms.billing.VoidService;
import com.vertex.csms.common.ApiException;
import com.vertex.csms.product.ProductImportService;
import com.vertex.csms.returns.ReturnService;
import com.vertex.csms.security.Role;
import com.vertex.csms.security.UserPrincipal;

/**
 * The demo story and the before-demo checklist, run against a real MySQL 8 database.
 *
 * Needs MySQL running and the csms user allowed on clothing_shop_test (db/create_db_user.sql). Run with:
 *     ./mvnw test -Dit.db=true
 * The test database is wiped and rebuilt from V1..V4 every run, so it never touches clothing_shop.
 */
@EnabledIfSystemProperty(named = "it.db", matches = "true")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/clothing_shop_test"
                + "?createDatabaseIfNotExist=true&connectionTimeZone=%2B05:30&forceConnectionTimeZoneToSession=true",
        "spring.flyway.clean-disabled=false"})
@AutoConfigureMockMvc
class DemoStoryIntegrationTest {

    @TestConfiguration
    static class CleanDatabase {
        @Bean
        FlywayMigrationStrategy cleanThenMigrate() {
            return flyway -> { flyway.clean(); flyway.migrate(); };
        }
    }

    static final UserPrincipal OWNER = new UserPrincipal(1L, "owner", "Renu Yadav", Role.OWNER);
    static final UserPrincipal STAFF = new UserPrincipal(2L, "staff", "Counter Staff", Role.SALES_STAFF);

    @Autowired BillingService billing;
    @Autowired HeldBillService held;
    @Autowired VoidService voids;
    @Autowired ReturnService returns;
    @Autowired ReceiptService receipts;
    @Autowired ProductImportService importer;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @BeforeEach
    void setUp() {
        // Keep the festive saree offer running whatever day the tests run.
        jdbc.update("UPDATE offer SET valid_from = NOW() - INTERVAL 1 DAY, valid_to = NOW() + INTERVAL 30 DAY");
        jdbc.update("INSERT IGNORE INTO customer (name, mobile, area, wa_opt_in, consent_at, consent_method, consent_by, credit_limit) "
                + "VALUES ('Team Tester (TEST)', '9000000001', 'Team Vertex', TRUE, NOW(), 'SIGNED_FORM', 1, 5000)");
        login(STAFF);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ---------- the demo bill ----------

    @Test
    void demoBillHasCorrectGstOfferStockAndAlert() {
        long customer = customerId();
        int blazerBefore = stock(192);

        var res = billing.complete(new CompleteRequest(customer,
                List.of(line(1, 1), line(192, 1), line(44, 1)),     // kurti 5%, blazer 18%, silk saree 5% after festive offer
                List.of(pay(PaymentMode.UPI, "5000"), pay(PaymentMode.CASH, "2310")), null));

        assertThat(res.invoiceNo()).matches("MC-\\d{4}-\\d{6}");
        assertThat(res.grandTotal()).isEqualByComparingTo("7310.00");
        assertThat(res.pointsEarned()).isEqualTo(73);
        assertThat(res.customerOptedIn()).isTrue();
        assertThat(res.stockAlerts()).anyMatch(a -> a.startsWith("LOW STOCK: Men Blazer 40/Charcoal"));

        ReceiptService.Receipt r = receipts.bySaleId(res.saleId(), false);
        assertThat(r.discountTotal()).isEqualByComparingTo("480.00");
        assertThat(r.taxableTotal()).isEqualByComparingTo("6590.48");
        assertThat(r.cgstTotal()).isEqualByComparingTo("359.76");
        assertThat(r.sgstTotal()).isEqualByComparingTo("359.76");
        assertThat(r.taxSummary()).extracting(BillingService.RateRow::rate).extracting(BigDecimal::intValue).containsExactly(5, 18);
        assertThat(r.lines().get(2).offerName()).isEqualTo("Festive 15% off on Sarees");
        assertThat(r.lines().get(2).gstRate()).isEqualByComparingTo("5");

        assertThat(stock(192)).isEqualTo(blazerBefore - 1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM alert_log WHERE alert_type = 'LOW_STOCK' AND entity_id = 192 "
                + "AND resolved_at IS NULL", Integer.class)).isEqualTo(1);
        assertLedgerConsistent();
    }

    @Test
    void wrongPaymentSavesNothing() {
        int sales = count("sale");
        int movements = count("stock_movement");
        int kurti = stock(2);
        assertCode(() -> billing.complete(new CompleteRequest(null, List.of(line(2, 1)),
                List.of(pay(PaymentMode.CASH, "100")), null)), "ERR-BIL-003");
        assertThat(count("sale")).isEqualTo(sales);
        assertThat(count("stock_movement")).isEqualTo(movements);
        assertThat(stock(2)).isEqualTo(kurti);
    }

    @Test
    void staffDiscountAboveTenPercentNeedsOwnerPinAndChangesTheSlab() {
        var cart = List.of(new CartLine(193L, 1, new BigDecimal("1000")));   // TC-BIL-010: blazer 3540 - 1000 = 2540 -> 5%
        assertCode(() -> billing.complete(new CompleteRequest(null, cart, List.of(pay(PaymentMode.CASH, "2540")), null)),
                "ERR-BIL-005");
        assertCode(() -> billing.complete(new CompleteRequest(null, cart, List.of(pay(PaymentMode.CASH, "2540")), "1111")),
                "ERR-BIL-005");
        var res = billing.complete(new CompleteRequest(null, cart, List.of(pay(PaymentMode.CASH, "2540")), "2580"));
        assertThat(receipts.bySaleId(res.saleId(), false).lines().get(0).gstRate()).isEqualByComparingTo("5");
    }

    // ---------- before-demo checklist: two counters, one last piece ----------

    @Test
    void twoCountersCannotBothSellTheLastPiece() throws Exception {
        assertThat(stock(56)).isEqualTo(1);   // Ready-to-Wear Lehenga L/Lavender
        int invoicesBefore = count("sale WHERE invoice_no IS NOT NULL");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<String> counter = () -> {
            login(STAFF);
            start.await();
            try {
                return billing.complete(new CompleteRequest(null, List.of(line(56, 1)),
                        List.of(pay(PaymentMode.CASH, "2950")), null)).invoiceNo();
            } catch (ApiException e) {
                return e.getCode();
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
        Future<String> a = pool.submit(counter), b = pool.submit(counter);
        start.countDown();
        List<String> results = List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
        pool.shutdown();

        assertThat(results).filteredOn(x -> x.startsWith("MC-")).hasSize(1);
        assertThat(results).filteredOn("ERR-INV-001"::equals).hasSize(1);
        assertThat(stock(56)).isZero();
        assertThat(count("sale WHERE invoice_no IS NOT NULL")).isEqualTo(invoicesBefore + 1);
        assertLedgerConsistent();
    }

    // ---------- returns ----------

    @Test
    void returnsFollowTheRules() {
        long vest = jdbc.queryForObject("SELECT variant_id FROM variant WHERE product_id = 37 AND stock_qty > 0 LIMIT 1", Long.class);
        BigDecimal vestPrice = jdbc.queryForObject("SELECT selling_price FROM variant WHERE variant_id = ?", BigDecimal.class, vest);
        var sale = billing.complete(new CompleteRequest(null, List.of(line(5, 2), line(vest, 1)),
                List.of(new PaymentRequest(PaymentMode.CASH, new BigDecimal("2100").add(vestPrice), null)), null));
        var bill = receipts.bySaleId(sale.saleId(), false);
        long kurtiLine = bill.lines().get(0).lineId(), vestLine = bill.lines().get(1).lineId();
        int kurtiAfterSale = stock(5);

        assertCode(() -> returns.process(new ReturnService.ReturnRequest(sale.invoiceNo(),
                List.of(new ReturnService.ReturnLine(vestLine, 1, false)), "Changed mind", null)), "ERR-RET-003");
        assertCode(() -> returns.process(new ReturnService.ReturnRequest("MC-0000-999999",
                List.of(new ReturnService.ReturnLine(kurtiLine, 1, false)), "Size issue", null)), "ERR-RET-002");

        var cn = returns.process(new ReturnService.ReturnRequest(sale.invoiceNo(),
                List.of(new ReturnService.ReturnLine(kurtiLine, 1, false)), "Size issue", null));
        assertThat(cn.cnNo()).matches("CN-\\d{4}-\\d{5}");
        assertThat(cn.amount()).isEqualByComparingTo("1050.00");
        assertThat(cn.saleStatus()).isEqualTo("PARTIALLY_RETURNED");
        assertThat(stock(5)).isEqualTo(kurtiAfterSale + 1);

        // A defective piece is written off, not put back on the shelf.
        returns.process(new ReturnService.ReturnRequest(sale.invoiceNo(),
                List.of(new ReturnService.ReturnLine(kurtiLine, 1, true)), "Defect", null));
        assertThat(stock(5)).isEqualTo(kurtiAfterSale + 1);
        assertCode(() -> returns.process(new ReturnService.ReturnRequest(sale.invoiceNo(),
                List.of(new ReturnService.ReturnLine(kurtiLine, 1, false)), "Size issue", null)), "ERR-RET-001");

        // The credit note pays for an exchange bill.
        var exchange = billing.complete(new CompleteRequest(null, List.of(line(7, 1)),
                List.of(new PaymentRequest(PaymentMode.CREDIT_NOTE, new BigDecimal("1050"), cn.cnNo())), null));
        assertThat(exchange.invoiceNo()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT balance FROM credit_note WHERE cn_no = ?", BigDecimal.class, cn.cnNo())).isZero();
        assertLedgerConsistent();
    }

    // ---------- void and hold ----------

    @Test
    void ownerVoidReversesStockCreditAndPoints() {
        long customer = customerId();
        int stockBefore = stock(6);
        BigDecimal dueBefore = jdbc.queryForObject("SELECT credit_due FROM customer WHERE customer_id = ?", BigDecimal.class, customer);
        int pointsBefore = jdbc.queryForObject("SELECT loyalty_points FROM customer WHERE customer_id = ?", Integer.class, customer);
        var sale = billing.complete(new CompleteRequest(customer, List.of(line(6, 2)),
                List.of(pay(PaymentMode.CREDIT, "2100")), null));

        assertCode(() -> voids.voidSale(sale.saleId(), new VoidService.VoidRequest("Billed by mistake", null)), "ERR-ACC-003");
        login(OWNER);
        assertCode(() -> voids.voidSale(sale.saleId(), new VoidService.VoidRequest("", null)), "ERR-VAL-001");
        voids.voidSale(sale.saleId(), new VoidService.VoidRequest("Billed by mistake", null));

        assertThat(receipts.bySaleId(sale.saleId(), false).status()).isEqualTo("CANCELLED");
        assertThat(stock(6)).isEqualTo(stockBefore);
        assertThat(jdbc.queryForObject("SELECT credit_due FROM customer WHERE customer_id = ?", BigDecimal.class, customer))
                .isEqualByComparingTo(dueBefore);
        assertThat(jdbc.queryForObject("SELECT loyalty_points FROM customer WHERE customer_id = ?", Integer.class, customer))
                .isEqualTo(pointsBefore);
        assertCode(() -> voids.voidSale(sale.saleId(), new VoidService.VoidRequest("again", null)), "ERR-BIL-002");
        assertLedgerConsistent();
    }

    @Test
    void heldBillDoesNotTouchStockAndResumesOnce() {
        int before = stock(4);
        var h = held.hold(new BillingService.CartRequest(null, List.of(new CartLine(4L, 2, new BigDecimal("50"))), null));
        assertThat(stock(4)).isEqualTo(before);
        assertThat(held.list()).anyMatch(m -> ((Number) m.get("held_id")).longValue() == h.heldId());

        var cart = held.resume(h.heldId());
        assertThat(cart.lines()).singleElement().satisfies(l -> {
            assertThat(l.quantity()).isEqualTo(2);
            assertThat(l.manualDiscount()).isEqualByComparingTo("50");
        });
        assertCode(() -> held.resume(h.heldId()), "ERR-BIL-404");
    }

    // ---------- import ----------

    @Test
    void importPreviewRejectsTheSeededProductsAsDuplicates() throws Exception {
        login(OWNER);
        var file = new MockMultipartFile("file", "products_import.csv", "text/csv",
                Files.readAllBytes(Path.of("db/products_import.csv")));
        var p = importer.preview(file);
        assertThat(p.totalRows()).isEqualTo(274);
        assertThat(p.invalid()).isEqualTo(274);
        assertThat(p.rows().get(0).messages()).anyMatch(m -> m.startsWith("ERR-PRD-002"));
    }

    @Test
    void importCommitCreatesProductsAndSkipsBadRows() throws Exception {
        login(OWNER);
        var file = new MockMultipartFile("file", "sample.csv", "text/csv",
                Files.readAllBytes(Path.of("db/sample_import_new_products.csv")));
        var p = importer.preview(file);
        assertThat(p.invalid()).isEqualTo(3);   // bad HSN, below cost, duplicate barcode
        var r = importer.commit(file);
        assertThat(r.rowsSkipped()).isEqualTo(3);
        assertThat(r.variantsCreated()).isEqualTo(p.valid() + p.warnings());
        assertThat(importer.preview(file).invalid()).isEqualTo(p.totalRows());   // second time: all duplicates
        assertLedgerConsistent();
    }

    // ---------- security over HTTP ----------

    @Test
    void staffNeverReceivesCostPricesAndCannotOpenReports() throws Exception {
        MockHttpSession staff = httpLogin("staff", "Staff@123");
        MockHttpSession owner = httpLogin("owner", "Owner@123");

        mvc.perform(get("/api/v1/variants").param("barcode", "2000000000015").session(staff))
                .andExpect(status().isOk()).andExpect(jsonPath("$.costPrice").doesNotExist());
        mvc.perform(get("/api/v1/products/1").session(staff))
                .andExpect(jsonPath("$.variants[0].costPrice").doesNotExist());
        mvc.perform(get("/api/v1/variants").param("barcode", "2000000000015").session(owner))
                .andExpect(jsonPath("$.costPrice").value(578.0));
        mvc.perform(get("/api/v1/reports/dashboard").session(staff))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ERR-ACC-003"));
        mvc.perform(get("/api/v1/reports/dashboard"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("ERR-ACC-004"));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"owner\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("ERR-ACC-001"));
        jdbc.update("UPDATE app_user SET failed_attempts = 0 WHERE username = 'owner'");
    }

    // ---------- helpers ----------

    private MockHttpSession httpLogin(String user, String password) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }

    private static void login(UserPrincipal p) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + p.role().name()))));
    }

    private static CartLine line(long variantId, int qty) {
        return new CartLine(variantId, qty, null);
    }

    private static PaymentRequest pay(PaymentMode mode, String amount) {
        return new PaymentRequest(mode, new BigDecimal(amount), null);
    }

    private long customerId() {
        return jdbc.queryForObject("SELECT customer_id FROM customer WHERE mobile = '9000000001'", Long.class);
    }

    private int stock(long variantId) {
        return jdbc.queryForObject("SELECT stock_qty FROM variant WHERE variant_id = ?", Integer.class, variantId);
    }

    private int count(String fromWhere) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + fromWhere, Integer.class);
    }

    private void assertLedgerConsistent() {
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM variant v
                LEFT JOIN (SELECT variant_id, SUM(qty_change) AS total FROM stock_movement GROUP BY variant_id) m USING (variant_id)
                WHERE v.stock_qty <> COALESCE(m.total, 0)""", Integer.class)).as("stock ledger mismatches").isZero();
    }

    private static void assertCode(Runnable call, String code) {
        assertThatThrownBy(call::run).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(code));
    }
}
