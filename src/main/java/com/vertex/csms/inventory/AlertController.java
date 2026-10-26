package com.vertex.csms.inventory;

import java.util.List;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.vertex.csms.common.ApiException;
import com.vertex.csms.security.CurrentUser;

/** Owner-only (see SecurityConfig): open alerts and acknowledge them (REQ-ALT-3). */
@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final AlertLogRepository alerts;

    public AlertController(AlertLogRepository alerts) {
        this.alerts = alerts;
    }

    @GetMapping
    public List<AlertLog> open() {
        return alerts.findTop50ByResolvedAtIsNullOrderByIdDesc();
    }

    @PostMapping("/{id}/resolve")
    @Transactional
    public void resolve(@PathVariable Long id) {
        alerts.findById(id).orElseThrow(() -> ApiException.notFound("ERR-ALT-404", "Alert not found."))
              .resolve(CurrentUser.id());
    }
}
