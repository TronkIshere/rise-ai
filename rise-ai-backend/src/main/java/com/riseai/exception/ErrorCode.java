package com.riseai.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication is required"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid email or password"),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Refresh token is invalid or expired"),
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "Account is suspended"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access is denied"),
    SELF_APPROVAL_NOT_ALLOWED(HttpStatus.FORBIDDEN, "You cannot review or approve a request you are a party to"),
    CONFLICT_OF_INTEREST(HttpStatus.FORBIDDEN, "This action conflicts with your role in the resource"),
    SELF_ROLE_CHANGE_NOT_ALLOWED(HttpStatus.FORBIDDEN, "You cannot change your own roles or account status"),
    DISPUTE_NOT_ELIGIBLE(HttpStatus.FORBIDDEN, "You are not eligible to open a dispute for this campaign"),
    SIMULATION_DISABLED(HttpStatus.FORBIDDEN, "Simulation endpoint is disabled"),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request validation failed"),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "Request is invalid"),
    UNSUPPORTED_CURRENCY(HttpStatus.BAD_REQUEST, "Currency is not supported"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed"),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "Email is already registered"),
    INVALID_STATE_TRANSITION(HttpStatus.CONFLICT, "Operation is not allowed in the current state"),
    CAMPAIGN_NOT_ACCEPTING_DONATIONS(HttpStatus.CONFLICT, "Campaign is not accepting donations"),
    DUPLICATE_OPERATION(HttpStatus.CONFLICT, "Operation has already been processed"),
    OPEN_REQUEST_EXISTS(HttpStatus.CONFLICT, "An open request already exists for this resource"),
    RESOURCE_IN_USE(HttpStatus.CONFLICT, "Resource is referenced by other records"),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "Resource was modified concurrently, retry the operation"),
    DATA_INTEGRITY_VIOLATION(HttpStatus.CONFLICT, "Request conflicts with existing data"),
    ESCROW_NOT_ACTIVE(HttpStatus.CONFLICT, "Escrow account does not accept this operation"),
    BUDGET_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY, "Budget amounts are inconsistent"),
    MILESTONE_BUDGET_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "Amount exceeds the remaining milestone budget"),
    DONATION_BELOW_MINIMUM(HttpStatus.UNPROCESSABLE_ENTITY, "Donation amount is below the campaign minimum"),
    INSUFFICIENT_AVAILABLE_BALANCE(HttpStatus.UNPROCESSABLE_ENTITY, "Escrow available balance is insufficient"),
    EVIDENCE_REQUIRED(HttpStatus.UNPROCESSABLE_ENTITY, "Required evidence has not been provided"),
    REFUND_EXCEEDS_REFUNDABLE(HttpStatus.UNPROCESSABLE_ENTITY, "Refund amount exceeds the refundable amount"),
    ORGANIZATION_NOT_VERIFIED(HttpStatus.UNPROCESSABLE_ENTITY, "Organization has not passed platform review"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");

    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
