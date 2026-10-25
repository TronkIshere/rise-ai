package com.riseai.service;

import com.riseai.dto.request.CreateRefundRequest;
import com.riseai.dto.response.RefundResponse;
import com.riseai.enums.RefundStatus;
import com.riseai.security.AuthUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface RefundService {
    RefundResponse request(AuthUser actor, UUID donationId, CreateRefundRequest request);

    RefundResponse request(AuthUser actor, UUID donationId, CreateRefundRequest request, String idempotencyKey);

    RefundResponse approve(AuthUser actor, UUID id, String reason);

    RefundResponse reject(AuthUser actor, UUID id, String reason);

    RefundResponse cancel(AuthUser actor, UUID id);

    RefundResponse record(AuthUser actor, UUID id);

    RefundResponse get(AuthUser actor, UUID id);

    Page<RefundResponse> mine(AuthUser actor, Pageable pageable);

    Page<RefundResponse> adminList(RefundStatus status, Pageable pageable);
}
