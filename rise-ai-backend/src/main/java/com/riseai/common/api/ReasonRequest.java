package com.riseai.common.api;

import jakarta.validation.constraints.Size;

public record ReasonRequest(@Size(max = 2000) String reason) {
    public static String of(ReasonRequest request) {
        return request == null ? null : request.reason();
    }
}
