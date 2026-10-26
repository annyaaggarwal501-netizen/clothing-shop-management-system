package com.vertex.csms.security;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.vertex.csms.common.ApiException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final int MAX_FAILED_ATTEMPTS = 5;

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final SecurityContextRepository contextRepository;

    public AuthController(AppUserRepository users, PasswordEncoder encoder, SecurityContextRepository contextRepository) {
        this.users = users;
        this.encoder = encoder;
        this.contextRepository = contextRepository;
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    // noRollbackFor: the failed-attempt counter must be saved even though we throw an error.
    @PostMapping("/login")
    @Transactional(noRollbackFor = ApiException.class)
    public UserPrincipal login(@RequestBody @jakarta.validation.Valid LoginRequest req,
                               HttpServletRequest request, HttpServletResponse response) {
        AppUser user = users.findByUsername(req.username().trim())
                .filter(AppUser::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "ERR-ACC-001", "Invalid username or password."));

        if (user.isLocked()) {
            throw new ApiException(HttpStatus.LOCKED, "ERR-ACC-002",
                    "Account locked after " + MAX_FAILED_ATTEMPTS + " failed attempts. Ask the Owner to unlock it.");
        }
        if (!encoder.matches(req.password(), user.getPasswordHash())) {
            user.setFailedAttempts(user.getFailedAttempts() + 1);
            if (user.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
                user.setLocked(true);
                throw new ApiException(HttpStatus.LOCKED, "ERR-ACC-002",
                        "Account locked after " + MAX_FAILED_ATTEMPTS + " failed attempts. Ask the Owner to unlock it.");
            }
            int left = MAX_FAILED_ATTEMPTS - user.getFailedAttempts();
            throw new ApiException(HttpStatus.UNAUTHORIZED, "ERR-ACC-001",
                    "Invalid username or password. " + left + " attempt(s) left before the account locks.");
        }
        user.setFailedAttempts(0);

        UserPrincipal principal = new UserPrincipal(user.getId(), user.getUsername(), user.getFullName(), user.getRole());
        var auth = new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));

        // New session id on login (protects against session fixation).
        HttpSession old = request.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        request.getSession(true);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        return principal;
    }

    @PostMapping("/logout")
    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    @GetMapping("/me")
    public UserPrincipal me() {
        return CurrentUser.get();
    }
}
