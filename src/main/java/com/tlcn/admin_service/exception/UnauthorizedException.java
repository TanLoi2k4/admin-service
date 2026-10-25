package com.tlcn.admin_service.exception;

/**
 * Exception for unauthorized access (401)
 */
public class UnauthorizedException extends BusinessException {
    public UnauthorizedException(String message) {
        super("UNAUTHORIZED", message, 401);
    }
}