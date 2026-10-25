package com.tlcn.admin_service.controller;

import com.tlcn.admin_service.enums.LogLevel;
import com.tlcn.admin_service.exception.BusinessException;
import com.tlcn.admin_service.exception.UnauthorizedException;
import com.tlcn.admin_service.dto.ResponseDTO;
import com.tlcn.admin_service.service.LogService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/logs")
public class AdminLogController {

    private final LogService logService;

    public AdminLogController(LogService logService) {
        this.logService = logService;
    }

    @Value("${admin-service.internal-log-token}")
    private String internalLogToken;

    /**
     * Log admin action from other services
     * This endpoint is called by vendor-service and customer-service
     * when admin performs actions on their resources
     */
    @PostMapping("/external-action")
    public ResponseEntity<ResponseDTO<Void>> logExternalAction(
            @RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
            @RequestParam(name = "userId", required = false) Long userId,
            @RequestParam(name = "username", required = false) String username,
            @RequestParam("action") String action,
            @RequestParam("resourceType") String resourceType,
            @RequestParam("resourceId") String resourceId,
            @RequestParam("level") LogLevel level,
            @RequestParam("message") String message,
            @RequestParam(name = "additionalData", required = false) String additionalData,
            @RequestParam(name = "ipAddress", required = false) String ipAddress,
            @RequestParam(name = "userAgent", required = false) String userAgent) {
        if (internalToken == null || !internalToken.equals(internalLogToken)) {
            throw new UnauthorizedException("Invalid internal token");
        }
        if (level == null) {
            throw new BusinessException("INVALID_LOG_LEVEL", "Log level is required");
        }

        logService.logAction(
            userId,
            username,
            action,
            resourceType,
            resourceId,
            level,
            message,
            additionalData,
            ipAddress,
            userAgent
        );

        return ResponseEntity.ok(new ResponseDTO<>(true, "Log recorded successfully", null));
    }
}