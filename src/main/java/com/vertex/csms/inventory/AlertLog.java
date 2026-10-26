package com.vertex.csms.inventory;

import java.time.LocalDateTime;

import jakarta.persistence.*;

@Entity
@Table(name = "alert_log")
public class AlertLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "alert_id")
    private Long id;

    private String alertType;
    private String severity;
    private Long entityId;
    private String message;

    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime resolvedAt;
    private Long resolvedBy;

    protected AlertLog() {}

    public AlertLog(String alertType, String severity, Long entityId, String message) {
        this.alertType = alertType;
        this.severity = severity;
        this.entityId = entityId;
        this.message = message.length() > 255 ? message.substring(0, 255) : message;
    }

    public void resolve(Long userId) {
        this.resolvedAt = LocalDateTime.now();
        this.resolvedBy = userId;
    }

    public Long getId() { return id; }
    public String getAlertType() { return alertType; }
    public String getSeverity() { return severity; }
    public Long getEntityId() { return entityId; }
    public String getMessage() { return message; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
}
