-- V1__schema.sql — CSMS prototype schema (MySQL 8.0, InnoDB, utf8mb4)
-- Matches SRS Section 10.2.2. Money = DECIMAL, never FLOAT/DOUBLE (SRS 2.5).
SET NAMES utf8mb4;

CREATE TABLE app_user (
  user_id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  username        VARCHAR(30)  NOT NULL UNIQUE,
  full_name       VARCHAR(80)  NOT NULL,
  password_hash   VARCHAR(100) NOT NULL,                -- BCrypt, cost 12
  role            ENUM('OWNER','SALES_STAFF') NOT NULL,
  owner_pin_hash  VARCHAR(100) NULL,
  failed_attempts INT NOT NULL DEFAULT 0,
  locked          BOOLEAN NOT NULL DEFAULT FALSE,
  is_active       BOOLEAN NOT NULL DEFAULT TRUE,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE gst_slab (
  slab_id        BIGINT AUTO_INCREMENT PRIMARY KEY,
  tax_class      ENUM('APPAREL','FABRIC') NOT NULL,
  min_taxable    DECIMAL(10,2) NOT NULL,
  max_taxable    DECIMAL(10,2) NULL,                    -- NULL = no upper limit
  rate           DECIMAL(4,2)  NOT NULL,
  effective_from DATE NOT NULL,
  effective_to   DATE NULL
) ENGINE=InnoDB;

CREATE TABLE category (
  category_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name        VARCHAR(50) NOT NULL,
  parent_id   BIGINT NULL,
  is_active   BOOLEAN NOT NULL DEFAULT TRUE,
  CONSTRAINT fk_cat_parent FOREIGN KEY (parent_id) REFERENCES category(category_id)
) ENGINE=InnoDB;

CREATE TABLE product (
  product_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
  category_id   BIGINT NOT NULL,
  name          VARCHAR(100) NOT NULL,
  brand         VARCHAR(50) NULL,
  fabric        VARCHAR(50) NULL,
  gender        ENUM('MEN','WOMEN','KIDS','UNISEX') NOT NULL,
  hsn_code      VARCHAR(8)  NOT NULL,
  tax_class     ENUM('APPAREL','FABRIC') NOT NULL DEFAULT 'APPAREL',
  is_returnable BOOLEAN NOT NULL DEFAULT TRUE,
  is_active     BOOLEAN NOT NULL DEFAULT TRUE,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_prod_cat FOREIGN KEY (category_id) REFERENCES category(category_id),
  CONSTRAINT chk_hsn CHECK (hsn_code REGEXP '^[0-9]{4,8}$')
) ENGINE=InnoDB;

CREATE TABLE variant (
  variant_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
  product_id    BIGINT NOT NULL,
  sku           VARCHAR(30) NOT NULL UNIQUE,
  barcode       VARCHAR(20) NOT NULL UNIQUE,
  size          VARCHAR(10) NOT NULL,
  colour        VARCHAR(30) NOT NULL,
  cost_price    DECIMAL(10,2) NOT NULL,
  selling_price DECIMAL(10,2) NOT NULL,
  mrp           DECIMAL(10,2) NOT NULL,
  stock_qty     INT NOT NULL DEFAULT 0,
  reorder_level INT NOT NULL DEFAULT 2,
  CONSTRAINT fk_var_prod FOREIGN KEY (product_id) REFERENCES product(product_id),
  CONSTRAINT uq_var_combo UNIQUE (product_id, size, colour),
  CONSTRAINT chk_stock CHECK (stock_qty >= 0),
  CONSTRAINT chk_price CHECK (selling_price > 0 AND selling_price <= mrp AND cost_price >= 0)
) ENGINE=InnoDB;

CREATE TABLE supplier (
  supplier_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name        VARCHAR(80) NOT NULL,
  phone       CHAR(10) NULL,
  gstin       VARCHAR(15) NULL
) ENGINE=InnoDB;

CREATE TABLE purchase (
  purchase_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  supplier_id BIGINT NOT NULL,
  bill_no     VARCHAR(30) NOT NULL,
  bill_date   DATE NOT NULL,
  created_by  BIGINT NOT NULL,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_pur_sup  FOREIGN KEY (supplier_id) REFERENCES supplier(supplier_id),
  CONSTRAINT fk_pur_user FOREIGN KEY (created_by)  REFERENCES app_user(user_id)
) ENGINE=InnoDB;

CREATE TABLE purchase_line (
  pline_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
  purchase_id BIGINT NOT NULL,
  variant_id  BIGINT NOT NULL,
  quantity    INT NOT NULL,
  unit_cost   DECIMAL(10,2) NOT NULL,
  CONSTRAINT fk_pl_pur FOREIGN KEY (purchase_id) REFERENCES purchase(purchase_id),
  CONSTRAINT fk_pl_var FOREIGN KEY (variant_id)  REFERENCES variant(variant_id),
  CONSTRAINT chk_pl_qty CHECK (quantity > 0)
) ENGINE=InnoDB;

CREATE TABLE stock_movement (
  movement_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  variant_id  BIGINT NOT NULL,
  type        ENUM('OPENING','PURCHASE','SALE','RETURN','ADJUSTMENT','VOID') NOT NULL,
  qty_change  INT NOT NULL,
  ref_type    VARCHAR(20) NULL,
  ref_id      BIGINT NULL,
  reason      VARCHAR(100) NULL,
  user_id     BIGINT NOT NULL,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_sm_var  FOREIGN KEY (variant_id) REFERENCES variant(variant_id),
  CONSTRAINT fk_sm_user FOREIGN KEY (user_id)    REFERENCES app_user(user_id),
  CONSTRAINT chk_sm_qty CHECK (qty_change <> 0)
) ENGINE=InnoDB;

CREATE TABLE customer (
  customer_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
  name           VARCHAR(80) NOT NULL,
  mobile         CHAR(10) NOT NULL UNIQUE,
  area           VARCHAR(60) NULL,
  birthday       DATE NULL,
  anniversary    DATE NULL,
  wa_opt_in      BOOLEAN NOT NULL DEFAULT FALSE,
  consent_at     DATETIME NULL,
  consent_method ENUM('IN_STORE_VERBAL','SIGNED_FORM','WHATSAPP_REPLY','QR_SCAN') NULL,
  consent_by     BIGINT NULL,
  loyalty_points INT NOT NULL DEFAULT 0,
  credit_limit   DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  credit_due     DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  is_active      BOOLEAN NOT NULL DEFAULT TRUE,
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_cus_consent_by FOREIGN KEY (consent_by) REFERENCES app_user(user_id),
  CONSTRAINT chk_mobile  CHECK (mobile REGEXP '^[6-9][0-9]{9}$'),
  CONSTRAINT chk_consent CHECK (wa_opt_in = FALSE OR (consent_at IS NOT NULL AND consent_method IS NOT NULL)),
  CONSTRAINT chk_cus_amt CHECK (loyalty_points >= 0 AND credit_limit >= 0 AND credit_due >= 0)
) ENGINE=InnoDB;

CREATE TABLE offer (
  offer_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
  name        VARCHAR(80) NOT NULL,
  type        ENUM('PERCENT','FLAT','BUY_X_GET_Y','BILL_VALUE') NOT NULL,
  value       DECIMAL(10,2) NOT NULL,
  category_id BIGINT NULL,
  product_id  BIGINT NULL,
  valid_from  DATETIME NOT NULL,
  valid_to    DATETIME NOT NULL,
  is_active   BOOLEAN NOT NULL DEFAULT TRUE,
  CONSTRAINT fk_off_cat  FOREIGN KEY (category_id) REFERENCES category(category_id),
  CONSTRAINT fk_off_prod FOREIGN KEY (product_id)  REFERENCES product(product_id),
  CONSTRAINT chk_off_dates CHECK (valid_to > valid_from)
) ENGINE=InnoDB;

CREATE TABLE sale (
  sale_id       BIGINT AUTO_INCREMENT PRIMARY KEY,
  invoice_no    VARCHAR(16) NULL UNIQUE,              -- MC-2627-000154, set on COMPLETED
  customer_id   BIGINT NULL,                          -- NULL = walk-in
  created_by    BIGINT NOT NULL,
  status        VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  doc_type      ENUM('TAX_INVOICE','BILL_OF_SUPPLY') NOT NULL DEFAULT 'TAX_INVOICE',
  subtotal      DECIMAL(10,2) NOT NULL DEFAULT 0,
  discount_total DECIMAL(10,2) NOT NULL DEFAULT 0,
  taxable_total DECIMAL(10,2) NOT NULL DEFAULT 0,
  cgst_total    DECIMAL(10,2) NOT NULL DEFAULT 0,
  sgst_total    DECIMAL(10,2) NOT NULL DEFAULT 0,
  round_off     DECIMAL(4,2)  NOT NULL DEFAULT 0,
  grand_total   DECIMAL(10,2) NOT NULL DEFAULT 0,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  completed_at  DATETIME NULL,
  CONSTRAINT fk_sale_cus  FOREIGN KEY (customer_id) REFERENCES customer(customer_id),
  CONSTRAINT fk_sale_user FOREIGN KEY (created_by)  REFERENCES app_user(user_id),
  CONSTRAINT chk_sale_status CHECK (status IN ('DRAFT','HELD','COMPLETED','PARTIALLY_RETURNED','RETURNED','CANCELLED')),
  CONSTRAINT chk_sale_tot CHECK (subtotal >= 0 AND grand_total >= 0)
) ENGINE=InnoDB;

CREATE TABLE sale_line (
  line_id       BIGINT AUTO_INCREMENT PRIMARY KEY,
  sale_id       BIGINT NOT NULL,
  variant_id    BIGINT NOT NULL,
  offer_id      BIGINT NULL,
  quantity      INT NOT NULL,
  unit_price    DECIMAL(10,2) NOT NULL,              -- tax-inclusive price charged
  unit_discount DECIMAL(10,2) NOT NULL DEFAULT 0,
  gst_rate      DECIMAL(4,2)  NOT NULL,
  taxable_value DECIMAL(10,2) NOT NULL,
  tax_amount    DECIMAL(10,2) NOT NULL,
  returned_qty  INT NOT NULL DEFAULT 0,
  CONSTRAINT fk_sl_sale  FOREIGN KEY (sale_id)    REFERENCES sale(sale_id),
  CONSTRAINT fk_sl_var   FOREIGN KEY (variant_id) REFERENCES variant(variant_id),
  CONSTRAINT fk_sl_offer FOREIGN KEY (offer_id)   REFERENCES offer(offer_id),
  CONSTRAINT chk_sl_qty  CHECK (quantity > 0 AND returned_qty >= 0 AND returned_qty <= quantity),
  CONSTRAINT chk_sl_disc CHECK (unit_discount >= 0)
) ENGINE=InnoDB;

CREATE TABLE payment (
  payment_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  sale_id    BIGINT NOT NULL,
  mode       ENUM('CASH','UPI','CARD','CREDIT','CREDIT_NOTE','POINTS') NOT NULL,
  amount     DECIMAL(10,2) NOT NULL,
  reference  VARCHAR(50) NULL,
  CONSTRAINT fk_pay_sale FOREIGN KEY (sale_id) REFERENCES sale(sale_id),
  CONSTRAINT chk_pay_amt CHECK (amount > 0)
) ENGINE=InnoDB;

CREATE TABLE credit_note (
  cn_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
  cn_no      VARCHAR(16) NOT NULL UNIQUE,             -- CN-2627-00021
  sale_id    BIGINT NOT NULL,
  amount     DECIMAL(10,2) NOT NULL,
  balance    DECIMAL(10,2) NOT NULL,
  valid_to   DATE NOT NULL,
  created_by BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_cn_sale FOREIGN KEY (sale_id)    REFERENCES sale(sale_id),
  CONSTRAINT fk_cn_user FOREIGN KEY (created_by) REFERENCES app_user(user_id),
  CONSTRAINT chk_cn_amt CHECK (amount > 0 AND balance >= 0 AND balance <= amount)
) ENGINE=InnoDB;

CREATE TABLE wa_message (
  message_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
  customer_id   BIGINT NOT NULL,
  mode          ENUM('CLICK_TO_CHAT','CLOUD_API') NOT NULL,
  purpose       ENUM('PRODUCT_SHARE','E_BILL','CAMPAIGN','REPLY') NOT NULL,
  template_name VARCHAR(60) NULL,
  status        VARCHAR(12) NOT NULL DEFAULT 'QUEUED',
  wamid         VARCHAR(100) NULL,
  attempts      TINYINT NOT NULL DEFAULT 0,
  error_code    VARCHAR(20) NULL,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  sent_at       DATETIME NULL,
  CONSTRAINT fk_wa_cus FOREIGN KEY (customer_id) REFERENCES customer(customer_id),
  CONSTRAINT chk_wa_status CHECK (status IN ('QUEUED','SENT','DELIVERED','READ','FAILED','ABANDONED'))
) ENGINE=InnoDB;

CREATE TABLE alert_log (
  alert_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
  alert_type  VARCHAR(30) NOT NULL,
  severity    VARCHAR(5)  NOT NULL,
  entity_id   BIGINT NULL,
  message     VARCHAR(255) NOT NULL,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  resolved_at DATETIME NULL,
  resolved_by BIGINT NULL,
  CONSTRAINT fk_alert_user FOREIGN KEY (resolved_by) REFERENCES app_user(user_id),
  CONSTRAINT chk_alert_sev CHECK (severity IN ('INFO','WARN','ERROR'))
) ENGINE=InnoDB;

CREATE INDEX idx_sale_created  ON sale(created_at);
CREATE INDEX idx_sm_variant    ON stock_movement(variant_id, created_at);
CREATE INDEX idx_product_name  ON product(name);
