package com.riseai.dto.request;

import com.riseai.enums.Role;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record UpdateRolesRequest(@NotEmpty Set<Role> roles) {
}
