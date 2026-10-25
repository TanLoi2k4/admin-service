package com.tlcn.admin_service.exception;

/**
 * Exception for forbidden access (403)
 */
public class ForbiddenException extends BusinessException {
    public ForbiddenException(String message) {
        super("FORBIDDEN", message, 403);
    }
}