# CSMS: Clothing Shop Management System (prototype)

This is a working prototype for **Mansi Collection**, built by Team Vertex. It follows SRS v1.0.1 and the 6-week prototype plan.

It runs the full demo story from start to finish:

> The Owner adds stock. Staff scan items and make a bill with correct GST. Stock goes down. The bill is shared on WhatsApp. The Owner sees it in today's report.

| | |
|---|---|
| Backend | Java 17+, Spring Boot 3.5, Spring Security (session cookie, 2 roles), Spring Data JPA + JdbcTemplate, Flyway |
| Database | MySQL 8.0, using the **unchanged** `V1__schema.sql` + `V2__seed_data.sql`, plus a small `V3` |
| Frontend | Plain HTML/CSS/JS in `src/main/resources/static` (no build step, works offline) |
| Excel | Apache POI |
| Tests | `TaxCalculatorTest` runs all 11 rows of `gst_test_cases.csv`; `DemoStoryIntegrationTest` runs the demo story and checklist against MySQL |

---

## 1. Set up a laptop (once)

1. Install **JDK 17 or newer** (Temurin works) and **MySQL 8.0 + Workbench**.
2. In Workbench, connect as `root` and run **`db/create_db_user.sql`**. It creates the empty `clothing_shop` database and the app login `csms` / `csms_dev_pw`.
3. Open the `csms` folder in IntelliJ IDEA. It is a normal Maven project.

## 2. Run the app

```bash
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
```
You can also run `CsmsApplication` from IntelliJ. On the first start, Flyway creates every table and loads the seed data (V1 → V2 → V3 → V4). Then open **http://localhost:8080**.

| Login | Password | Role | Owner PIN |
|---|---|---|---|
| `owner` | `Owner@123` | OWNER: everything | `2580` |
| `staff` | `Staff@123` | SALES_STAFF: billing, products (no cost prices), customers, returns | |

You can change the database settings with environment variables, so no password goes into the code: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`. Other variables: `PUBLIC_BASE_URL` (see WhatsApp below) and `LINK_SECRET`.

### Team test customers (for WhatsApp)
After the first start, edit **`db/team_test_customers.sql`**. Replace each `REPLACE_ME` with a team member's own number (with their permission). Then run the file in Workbench. These are the **only** customers who are opted in. The 30 seed customers are opted out, and the app refuses to message them (ERR-WA-001).

### Same data for everyone
To rebuild from scratch:
1. Stop the app.
2. Run `db/reset_database.sql`.
3. Start the app again. Flyway reloads V1 + V2 + V3 + V4.
4. Run `team_test_customers.sql` again.

### Tests
```bash
./mvnw test                 # 14 GST unit tests (no database needed); the integration tests are skipped
./mvnw test -Dit.db=true    # + 10 integration tests against MySQL (needs db/create_db_user.sql run once)
scripts/race_test.sh        # app must be running; two counters, one last piece, by real HTTP calls
```
The integration tests use their own database, `clothing_shop_test`, which Flyway wipes and rebuilds on every run, so they never touch `clothing_shop`. They cover:
- the demo bill (₹7,310, GST 5%/18%, festive offer, low-stock alert)
- a wrong payment saving nothing
- the staff discount limit and Owner PIN (TC-BIL-010)
- two counters racing for the last piece
- returns: non-returnable items, defective write-off, a credit note used for an exchange
- void reversing stock, credit and loyalty points
- hold and resume
- product import
- staff never receiving cost prices over HTTP

Every test also checks that the stock history still adds up.

---

## 3. What is in the prototype

### The 8 screens (+ receipt and e-bill)
| Screen | File | Who |
|---|---|---|
| Login | `login.html` | everyone |
| Owner dashboard + sales report + Excel export | `dashboard.html` | Owner |
| Product list & search, WhatsApp share, CSV/Excel import | `products.html` | both (staff: no cost; import: Owner) |
| Add/edit product (size × colour grid) | `product-edit.html` | Owner edits, staff view |
| Stock-in | `stock-in.html` | Owner |
| POS billing, hold/resume bills | `pos.html` | both |
| Customers (consent, history) | `customers.html` | both |
| Returns + credit note / exchange | `returns.html` | both |
| Thermal receipt (80 mm print / PDF), Void bill | `receipt.html?id=` | both (void: Owner, same day) |
| E-bill opened from WhatsApp | `ebill.html?t=` | public, signed link |

### API
All endpoints are under `/api/v1`. Errors come back as `{code, message, field, timestamp}` using the SRS error codes.

| Method | Endpoint | Who |
|---|---|---|
| POST | `/auth/login`, `/auth/logout` · GET `/auth/me` | everyone |
| GET | `/variants?barcode=` (or `?sku=`), `/variants/search?q=`, `/variants/low-stock` | Owner, Staff |
| GET / POST / PUT | `/products`, `/products/{id}` | read: both · create/edit: Owner |
| GET | `/categories` | both |
| POST / GET | `/purchases` (stock-in), GET `/suppliers` | Owner |
| POST | `/sales/quote` (price the cart), `/sales` (complete) · GET `/sales/{id}`, `/sales?date=` | Owner, Staff |
| POST / GET / DELETE | `/sales/hold`, `/sales/held`, `/sales/held/{id}/resume`, `/sales/held/{id}` | Owner, Staff |
| POST | `/sales/{id}/void` `{reason, ownerPin}` | Owner (or Staff with the Owner PIN) |
| POST | `/products/import/preview`, `/products/import/commit` (multipart `file`) | Owner |
| GET | `/customers?mobile=` / `?q=`, `/customers/lookup?mobile=`, `/customers/{id}/history` | Owner, Staff |
| POST / PUT | `/customers`, `/customers/{id}`, `/customers/{id}/consent` | Owner, Staff |
| GET / POST | `/returns/lookup?invoice=`, `/returns` · GET `/credit-notes/{cnNo}` | Owner, Staff |
| POST / GET | `/whatsapp/ebill/{saleId}`, `/whatsapp/product/{id}`, `/whatsapp/log` | Owner, Staff |
| GET | `/reports/dashboard`, `/reports/sales?from=&to=`, `/reports/sales.xlsx?from=&to=` | Owner |
| GET / POST | `/alerts`, `/alerts/{id}/resolve`, `/users`, `/users/{id}/unlock` | Owner |
| GET | `/public/ebill/{token}`, `/public/shop` | no login |

A Postman collection is in `docs/CSMS.postman_collection.json`.

### Business rules implemented
- **GST (REQ-BIL-2..5).** Rates come from the `gst_slab` table. APPAREL tests the 5% slab first: if net ÷ 1.05 ≤ ₹2,500, the rate is 5%, otherwise 18%. FABRIC is 5% at any price. Discounts apply **before** tax. CGST = tax/2 rounded half-up, and SGST = the rest. WARN-GST-001 appears for net prices from ₹2,625.01 to ₹2,950. The receipt shows a tax summary for each rate.
- **Atomic sale (REQ-BIL-7).** One `@Transactional` method does all of this together:
  - locks the variants (`SELECT … FOR UPDATE`, in id order)
  - prices the cart again on the server
  - checks that payments equal the rounded total
  - inserts the sale, lines and payments
  - deducts stock with SALE movements
  - updates loyalty points and credit
  - assigns an invoice number

  If anything fails, nothing is saved.
- **Invoice numbers.** `MC-2627-000001` restarts each financial year. They come from `doc_sequence` (V3), which is locked inside the sale transaction, so numbers have no gaps and no duplicates. Credit notes use `CN-2627-00001`.
- **Offers.** The best item-level PERCENT/FLAT offer is applied automatically, by product or category (including the parent category). For example, "Festive 15% off on Sarees" runs 1 Oct – 15 Nov 2026. A manual discount does not stack with an offer: the larger one wins. Staff can give up to 10%; above that needs the Owner PIN (ERR-BIL-005).
- **Hold bill (F8).** Parks the cart as a HELD sale (no invoice number, no stock change), so the counter can serve the next customer. Any counter can resume it. Resuming while another bill is open parks that bill first. Held bills older than 24 hours are discarded.
- **Void bill (REQ-BIL-9).** Owner only (or Staff with the Owner PIN), only on the same day, and a reason is required. It puts stock back with VOID movements, takes back loyalty points, reduces credit due and restores the balance of any credit note used to pay. The invoice number stays in the series, marked CANCELLED, so there are no GST gaps. A VOID_BILL alert goes to the dashboard. For older bills, use Returns.
- **Bulk import (REQ-PRD-5).** Upload a .csv or .xlsx; the column format is in `db/products_import.csv`.
  - **Preview** checks every row and marks it VALID, WARNING or INVALID, with error codes. Nothing is saved at this step.
  - Errors it catches: duplicate barcodes or sizes/colours (ERR-PRD-002), selling price below cost (ERR-PRD-003), bad HSN, unknown category, files over 5 MB or empty (ERR-PRD-001), and wrong file types (ERR-PRD-005).
  - Warnings: WARN-GST-001, an HSN that doesn't match the tax class, and a category name used under more than one parent (write `Kids > Winterwear` to choose).
  - **Import** saves the good rows in one transaction. Rows for the same product name and category become one product. An existing product gets the new sizes and colours added.
  - Gender comes from the category's parent. Innerwear, blouses, nightwear and infant wear default to non-returnable.
  - `db/sample_import_new_products.csv` is a ready-made demo file: 8 good rows, 3 deliberately bad.
- **Payments.** Cash (with change), UPI, Card, Credit (needs a customer; warns when over the limit) and Credit note (balance and expiry are checked). Split payments are allowed.
- **Stock.** Every change goes through `StockService`, which writes `stock_movement`. Stock cannot go negative (ERR-INV-001). LOW_STOCK and OUT_OF_STOCK alerts are raised without duplicates and close themselves after a stock-in. Stock-in updates the weighted-average cost.
- **Returns.** Only within 7 days; after that the Owner PIN is needed (WARN-RET-001). Non-returnable items are blocked (ERR-RET-003), and you can't return more than was sold (ERR-RET-001). Returned items go back into stock; a "defective" tick writes them off instead. The credit note is valid for 90 days. Loyalty points are reversed. "Exchange" opens the POS with the credit note already applied as a payment.
- **Security.**
  - BCrypt cost 12 for passwords.
  - The account locks after 5 failed logins; the Owner can unlock it.
  - Session cookie: HttpOnly, SameSite=Strict, 30-minute idle timeout.
  - The server checks the role on every call.
  - **Cost prices are never sent to Staff.** The field is left out of the JSON.
- **WhatsApp (Click-to-Chat mode).** The app builds a `wa.me/91…` link with the message already filled in, and staff press Send.
  - **E-bill:** a signed link (HMAC) to `ebill.html`, so other bills can't be opened by guessing numbers.
  - **Product share:** the browser draws a product card image. On a phone it opens the share sheet with the image and text. On a PC it downloads the image and opens WhatsApp Web.
  - Every link is checked against consent, and messages are logged in `wa_message`.

### What V3 and V4 add (V1/V2 are untouched)
- **V3:** `doc_sequence` (invoice and credit note counters), `credit_note.reason`, and two indexes.
- **V4:** `sale.void_reason`, `voided_by` and `voided_at`.

### Not in the prototype (later versions per the SRS)
Label PDF, PDF reports, WhatsApp Cloud API and campaigns, points redemption, credit repayment ledger, backups, audit log table, HTTPS setup and the QR code on receipts.

---

## 4. WhatsApp from a phone
The e-bill link has to open on the customer's phone, so the phone must be able to reach the laptop:
- **Same Wi-Fi:** find the laptop's IP (`ipconfig` / `ifconfig`). Start the app with `PUBLIC_BASE_URL=http://192.168.x.x:8080`, and allow port 8080 in the firewall.
- **Anywhere:** use a tunnel (for example `ngrok http 8080`) and set `PUBLIC_BASE_URL` to the tunnel URL.

## 5. Project structure
```
csms/
├── src/main/java/com/vertex/csms/
│   ├── common/      ApiException, error handler, Money, FinancialYear, DocNumberService, settings
│   ├── security/    AppUser, login/lockout, roles, Owner PIN
│   ├── product/     Product, Variant, Category, EAN-13 barcodes, product + variant APIs
│   ├── inventory/   StockService (the only place stock changes), StockMovement, Purchase, alerts
│   ├── billing/     TaxCalculator, GstSlab, Offer, Sale, SaleLine, Payment, BillingService, receipts
│   ├── returns/     ReturnService, CreditNote
│   ├── customer/    Customer, consent, history
│   ├── messaging/   WhatsApp links, signed e-bill links, wa_message log
│   └── report/      dashboard, sales report, Excel export
├── src/main/resources/
│   ├── db/migration/  V1__schema.sql, V2__seed_data.sql, V3__prototype_additions.sql, V4__void_bill.sql
│   ├── static/        the screens (HTML, css/, js/)
│   └── application.properties
├── src/test/java/     TaxCalculatorTest (+ gst_test_cases.csv), DemoStoryIntegrationTest (MySQL)
├── db/                create_db_user.sql, reset_database.sql, week1_queries.sql, team_test_customers.sql,
│                      products_import.csv, sample_import_new_products.csv, customers.csv
├── scripts/           race_test.sh
└── docs/              DEMO_SCRIPT.md, Postman collection, data pack README
```
