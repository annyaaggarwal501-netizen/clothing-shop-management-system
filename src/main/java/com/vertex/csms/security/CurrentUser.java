package com.vertex.csms.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.vertex.csms.common.ApiException;

/** Static access to the logged-in user inside services. */
public final class CurrentUser {

    private CurrentUser() {}

    public static UserPrincipal get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal p)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "ERR-ACC-004", "Session expired. Please log in again.");
        }
        return p;
    }

    public static Long id() {
        return get().id();
    }

    public static boolean isOwner() {
        return get().isOwner();
    }
}
