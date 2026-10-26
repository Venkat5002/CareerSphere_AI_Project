package com.careersphere.security;

import com.careersphere.entity.Role;

/**
 * The logged-in user, taken from the JWT on every request.
 * Controllers receive it with @AuthenticationPrincipal - no hardcoded IDs anywhere.
 */
public record AuthUser(Long id, String email, String fullName, Role role) {
}
