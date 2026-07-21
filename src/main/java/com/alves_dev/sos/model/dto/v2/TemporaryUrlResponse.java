package com.alves_dev.sos.model.dto.v2;

import java.time.Instant;

public record TemporaryUrlResponse(String url, Instant expiresAt) {
}
