package com.riseai.security;

import com.riseai.enums.Role;
import com.riseai.exception.ApplicationException;
import com.riseai.exception.ErrorCode;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record AuthUser(UUID id, String email, Set<Role> roles) {
    public AuthUser {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    public static AuthUser require(AuthUser user) {
        if (user == null) {
            throw new ApplicationException(ErrorCode.UNAUTHENTICATED);
        }
        return user;
    }

    public AuthUser requireRole(Role role) {
        if (!roles.contains(role)) {
            throw new ApplicationException(ErrorCode.ACCESS_DENIED, "Role " + role + " is required");
        }
        return this;
    }

    public AuthUser requireAnyRole(Role... options) {
        for (Role role : options) {
            if (roles.contains(role)) {
                return this;
            }
        }
        throw new ApplicationException(ErrorCode.ACCESS_DENIED);
    }

    public boolean has(Role role) {
        return roles.contains(role);
    }

    public boolean isAdmin() {
        return has(Role.ADMIN);
    }

    public boolean is(UUID userId) {
        return id.equals(userId);
    }

    public List<SimpleGrantedAuthority> authorities() {
        return roles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r.name())).toList();
    }
}
