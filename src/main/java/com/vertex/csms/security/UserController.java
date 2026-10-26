package com.vertex.csms.security;

import java.util.List;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.vertex.csms.common.ApiException;

/** Owner-only: see users and unlock a locked account (REQ-ACC-4). */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final AppUserRepository users;

    public UserController(AppUserRepository users) {
        this.users = users;
    }

    public record UserRow(Long id, String username, String fullName, Role role, int failedAttempts, boolean locked) {}

    @GetMapping
    public List<UserRow> list() {
        return users.findAll().stream()
                .map(u -> new UserRow(u.getId(), u.getUsername(), u.getFullName(), u.getRole(), u.getFailedAttempts(), u.isLocked()))
                .toList();
    }

    @PostMapping("/{id}/unlock")
    @Transactional
    public void unlock(@PathVariable Long id) {
        AppUser u = users.findById(id).orElseThrow(() -> ApiException.notFound("ERR-ACC-001", "User not found."));
        u.setLocked(false);
        u.setFailedAttempts(0);
    }
}
