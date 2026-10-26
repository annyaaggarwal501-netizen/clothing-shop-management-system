-- V4__void_bill.sql — who voided a bill, when and why (REQ-BIL-9: Owner only, same day, with a reason).
ALTER TABLE sale
  ADD COLUMN void_reason VARCHAR(100) NULL,
  ADD COLUMN voided_by   BIGINT NULL,
  ADD COLUMN voided_at   DATETIME NULL,
  ADD CONSTRAINT fk_sale_voided_by FOREIGN KEY (voided_by) REFERENCES app_user(user_id);
