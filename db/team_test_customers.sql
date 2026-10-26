-- team_test_customers.sql — run MANUALLY after the seed (NOT a Flyway migration).
-- These are the ONLY customers who may receive WhatsApp test messages.
-- 1) Replace each REPLACE_ME with a team member's own 10-digit mobile number (with their permission).
-- 2) Run this file. The CHECK constraint rejects the row until a valid number is entered.
INSERT INTO customer (name, mobile, area, wa_opt_in, consent_at, consent_method, consent_by) VALUES
  ('Mridul Patwal (TEST)',    'REPLACE_ME', 'Team Vertex', TRUE, NOW(), 'SIGNED_FORM', 1),
  ('Ananya Agarwal (TEST)',   'REPLACE_ME', 'Team Vertex', TRUE, NOW(), 'SIGNED_FORM', 1),
  ('Akshat Raj (TEST)',       'REPLACE_ME', 'Team Vertex', TRUE, NOW(), 'SIGNED_FORM', 1),
  ('Kunal Ranga (TEST)',      'REPLACE_ME', 'Team Vertex', TRUE, NOW(), 'SIGNED_FORM', 1),
  ('Amgothu Narender (TEST)', 'REPLACE_ME', 'Team Vertex', TRUE, NOW(), 'SIGNED_FORM', 1);
