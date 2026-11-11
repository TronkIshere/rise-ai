package com.riseai.avalanche.api;

import java.time.Instant;

public record ApiError(Instant timestamp, int status, String code, String path) {
}
