package com.vertex.csms.messaging;

import java.time.LocalDateTime;

import jakarta.persistence.*;

/** Log of every WhatsApp message the app prepared (REQ-WA-7). */
@Entity
@Table(name = "wa_message")
public class WaMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    private Long id;

    private Long customerId;
    private String mode;
    private String purpose;
    private String templateName;
    private String status;
    private int attempts;

    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime sentAt;

    protected WaMessage() {}

    /** Click-to-Chat: the app builds the link and staff press Send in WhatsApp, so we log it as SENT. */
    public static WaMessage clickToChat(Long customerId, String purpose) {
        WaMessage m = new WaMessage();
        m.customerId = customerId;
        m.mode = "CLICK_TO_CHAT";
        m.purpose = purpose;
        m.status = "SENT";
        m.attempts = 1;
        m.sentAt = LocalDateTime.now();
        return m;
    }

    public Long getId() { return id; }
}
