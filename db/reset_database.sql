-- "Same data for everyone": drop and recreate the database, then start the app again.
-- Flyway re-runs V1 (schema), V2 (seed data) and V3 (prototype additions) on startup.
-- Afterwards run team_test_customers.sql again (with your real team numbers).
DROP DATABASE IF EXISTS clothing_shop;
CREATE DATABASE clothing_shop CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
