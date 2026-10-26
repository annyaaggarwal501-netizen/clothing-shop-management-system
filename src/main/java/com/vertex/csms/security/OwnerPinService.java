package com.vertex.csms.security;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.vertex.csms.common.ApiException;

/** Owner PIN for in-flow approvals: discount override, late return (REQ-ACC-5). */
@Service
public class OwnerPinService {

    private final AppUserRepository users;
    private final PasswordEncoder encoder;

    public OwnerPinService(AppUserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    public boolean matches(String pin) {
        if (pin == null || !pin.matches("\\d{4,6}")) {
            return false;
        }
        return users.findByRoleAndIsActiveTrue(Role.OWNER).stream()
                .anyMatch(u -> u.getOwnerPinHash() != null && encoder.matches(pin, u.getOwnerPinHash()));
    }

    /** The logged-in Owner is already trusted; Staff need a valid Owner PIN. */
    public void requireOwnerApproval(String pin, String what) {
        if (CurrentUser.isOwner()) {
            return;
        }
        if (!matches(pin)) {
            throw ApiException.forbidden("ERR-ACC-003", what + " needs the Owner PIN. The PIN is missing or incorrect.");
        }
    }
}
