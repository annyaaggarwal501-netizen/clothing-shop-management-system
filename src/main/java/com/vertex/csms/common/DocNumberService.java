package com.vertex.csms.common;

import java.time.LocalDate;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Invoice and credit-note numbers (REQ-BIL-8, REQ-RET-4).
 * Runs inside the caller's transaction: the doc_sequence row stays locked until the sale commits,
 * so numbers are never duplicated, and a rolled-back sale gives its number back (no gaps).
 */
@Service
public class DocNumberService {

    private final JdbcTemplate jdbc;

    public DocNumberService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String nextInvoiceNo(LocalDate date) {
        String fy = FinancialYear.code(date);
        return String.format("MC-%s-%06d", fy, next("INV-" + fy));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String nextCreditNoteNo(LocalDate date) {
        String fy = FinancialYear.code(date);
        return String.format("CN-%s-%05d", fy, next("CN-" + fy));
    }

    private long next(String key) {
        // One statement that creates the row the first time, or bumps it and takes an exclusive row lock.
        // The lock is held until the sale commits, so no other counter can get the same number.
        jdbc.update("INSERT INTO doc_sequence (seq_key, next_val) VALUES (?, 2) "
                + "ON DUPLICATE KEY UPDATE next_val = next_val + 1", key);
        return jdbc.queryForObject("SELECT next_val - 1 FROM doc_sequence WHERE seq_key = ?", Long.class, key);
    }
}
