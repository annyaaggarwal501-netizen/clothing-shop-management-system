package com.vertex.csms.report;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.vertex.csms.common.ApiException;

/** Owner only (SecurityConfig: /api/v1/reports/**). */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        return service.dashboard();
    }

    @GetMapping("/sales")
    public Map<String, Object> sales(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        check(from, to);
        return service.sales(from, to, true);
    }

    @GetMapping("/sales.xlsx")
    public ResponseEntity<byte[]> salesExcel(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) throws IOException {
        check(from, to);
        byte[] file = service.salesXlsx(from, to);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"sales_" + from + "_to_" + to + ".xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(file);
    }

    private static void check(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw ApiException.badRequest("ERR-VAL-001", "'To' date must be on or after 'From' date.", "to");
        }
        if (from.plusDays(366).isBefore(to)) {
            throw ApiException.badRequest("ERR-VAL-001", "Choose a period of at most one year.", "from");
        }
    }
}
