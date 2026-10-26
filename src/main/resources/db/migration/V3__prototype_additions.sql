-- V3__prototype_additions.sql — small additions the prototype needs on top of the SRS schema.
-- V1 and V2 are kept exactly as delivered; any change goes in a new numbered file (REQ-MNT-3).

-- Gap-free document numbers per financial year (REQ-BIL-8, REQ-RET-4).
-- The billing transaction locks the row (SELECT ... FOR UPDATE), so two counters can never get
-- the same number. seq_key examples: 'INV-2627' -> MC-2627-000001, 'CN-2627' -> CN-2627-00001.
CREATE TABLE doc_sequence (
  seq_key  VARCHAR(20) PRIMARY KEY,
  next_val BIGINT NOT NULL
) ENGINE=InnoDB;

-- Return reason per credit note (REQ-RET-2).
ALTER TABLE credit_note ADD COLUMN reason VARCHAR(100) NULL;

-- Lets the "today's sales" and alert queries use an index.
CREATE INDEX idx_sale_completed ON sale(completed_at);
CREATE INDEX idx_alert_open ON alert_log(alert_type, entity_id, resolved_at);
