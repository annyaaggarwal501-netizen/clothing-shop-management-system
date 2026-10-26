-- Run ONCE on each laptop as the MySQL root user (MySQL Workbench or the mysql command line).
-- Creates the empty database and the app's own login. Flyway then creates the tables and loads
-- the seed data the first time the Spring Boot app starts.
CREATE DATABASE IF NOT EXISTS clothing_shop CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'csms'@'localhost' IDENTIFIED BY 'csms_dev_pw';
GRANT ALL PRIVILEGES ON clothing_shop.* TO 'csms'@'localhost';
-- Used only by the automated integration tests (./mvnw test -Dit.db=true); wiped on every test run.
GRANT ALL PRIVILEGES ON clothing_shop_test.* TO 'csms'@'localhost';
FLUSH PRIVILEGES;
