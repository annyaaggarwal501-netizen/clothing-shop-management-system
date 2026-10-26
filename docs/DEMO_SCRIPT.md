# Demo script (about 5 minutes)

Before you start:
1. Rebuild the database: run `db/reset_database.sql`, then start the app.
2. Run `db/team_test_customers.sql` with the team's real numbers.
3. Keep a phone with WhatsApp on the same Wi-Fi as the laptop (see README §4).

The barcodes below come from the seed data. If you have no scanner, type them into the POS box and press Enter.

| Item | Barcode | Price | GST | Stock at start |
|---|---|---|---|---|
| Floral Cotton Kurti S/Blue | `2000000000015` | ₹1,050 | 5% | 10 |
| Men Blazer 40/Charcoal | `2000000001920` | ₹3,540 | 18% (taxable ₹3,000) | 2 → after sale 1 = **low-stock alert** |
| Banarasi Silk Saree Free/Red | `2000000000442` | ₹3,200 → **₹2,720** with festive 15% | 5% (saree = fabric) | 5 |

## 1. Owner: stock-in (≈45 s)
1. Log in as `owner` / `Owner@123`. The dashboard opens.
2. Go to **Stock-in**. Supplier: *Jaipur Weaves Wholesale*. Bill no.: `JW-2611`.
3. Scan `2000000000015` and set Qty to **5**. Save. The kurti's stock goes from 10 to 15.
4. Log out.

## 2. Staff: the bill (≈2 min)
1. Log in as `staff` / `Staff@123`. The POS opens.
2. Scan the three barcodes above. Point out:
   - The saree gets **"Festive 15% off on Sarees" automatically** (−₹480) and stays at **5%**, because sarees are fabric.
   - The blazer is **18%**. The kurti is **5%**.
3. Customer: type the team member's mobile and click **Find**. If they are new, register them inline and tick WhatsApp consent.
4. The totals panel shows:

   | Line | Amount |
   |---|---|
   | Gross | ₹7,790.00 |
   | Discount | −₹480.00 |
   | Taxable value | ₹6,590.48 |
   | CGST 2.5% / SGST 2.5% | ₹89.76 / ₹89.76 |
   | CGST 9% / SGST 9% | ₹270.00 / ₹270.00 |
   | **Total** | **₹7,310** |

5. Payment is Cash ₹7,310. Enter cash received ₹8,000; the change shows ₹690. (You can also show a split: **+ Split payment** → UPI.)
6. Press **F9**. The sale saves as **MC-2627-000001**. The modal shows the change, the loyalty points earned and **"LOW STOCK: Men Blazer 40/Charcoal – 1 left"**.
7. **Print receipt**. The 80 mm receipt has separate 5% and 18% lines in the GST summary, plus "You saved ₹480". With no printer, choose *Save as PDF*.
8. **Send e-bill on WhatsApp**. WhatsApp opens with the message already filled in. Press Send, and the team member opens the e-bill link on their phone.

## 3. Owner: dashboard (≈1 min)
1. Log out and log in as `owner`.
2. The dashboard shows:
   - Sales today ₹7,310, 1 bill
   - The GST table: 5% (taxable ₹3,590.48) and 18% (taxable ₹3,000)
   - The LOW_STOCK alert for the blazer
3. Sales report → *This month* → **Export to Excel**. The file has Summary, Bills, Daily, HSN summary and Credit notes sheets.

## Optional extras (if time allows)
- **Return / exchange:** Returns → `MC-2627-000001` → tick the kurti → *Exchange now* → Process. You get credit note CN-2627-00001 for ₹1,050. *Start exchange bill* opens the POS with the credit note already applied.
- **Hold bill:** scan two items, press **F8**, bill another customer, then use **Held bills → Resume**.
- **Void:** as Owner, open a bill made today from the dashboard's *Recent bills* → **Void bill** → enter a reason. The receipt shows VOID, and stock, points and credit are reversed. The dashboard shows a VOID_BILL alert.
- **Bulk import:** Products → **Import CSV / Excel** → `db/sample_import_new_products.csv` → *Check file*. You get 7 valid, 1 warning (WARN-GST-001) and 3 invalid rows, each with its error code. *Import 8 rows* creates 4 products and adds an XXL to Floral Cotton Kurti. Importing the data pack's `products_import.csv` shows all 274 rows as duplicates (ERR-PRD-002).
- **Non-returnable:** sell a Cotton Vest, then try to return it. You get ERR-RET-003.
- **Discount changes the slab (TC-BIL-010):** blazer + Edit discount ₹1,000 → 5%. Staff need Owner PIN `2580`.
- **Consent rule:** attach a dummy customer (e.g. `9688281126`). No WhatsApp button appears, and the API refuses with ERR-WA-001.
- **Cost hidden:** open a product as staff. There is no cost column, and the JSON has no `costPrice`.

## Before-demo checklist
| Check | How |
|---|---|
| All 11 GST test cases pass | `./mvnw test`: 14 tests, 0 failures |
| Whole story works on MySQL | `./mvnw test -Dit.db=true`: 24 tests, 0 failures |
| Staff can't see cost prices | Open a product as staff, or call `GET /api/v1/variants?barcode=…` as staff in Postman |
| Two people can't both buy the last piece | `scripts/race_test.sh` prints PASS |
| Receipt prints (or saves as PDF) | Receipt page → Print / Save as PDF |
| WhatsApp tested with team numbers only | Only `team_test_customers.sql` rows are opted in |
| Database freshly rebuilt from V1 + V2 | `db/reset_database.sql`, then restart the app |
| Ledger is consistent | Query 7 in `db/week1_queries.sql` returns 0 |
