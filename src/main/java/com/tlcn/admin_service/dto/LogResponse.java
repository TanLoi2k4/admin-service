package com.tlcn.admin_service.dto;

import com.tlcn.admin_service.enums.LogLevel;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class LogResponse {
    private Long id;
    private Long userId;
    private String username;
    private String action;
    private String resourceType;
    private String resourceId;
    private LogLevel level;
    private String message;
    private String additionalData;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime createdAt;

    public LogResponse(
            Long id,
            Long userId,
            String username,
            String action,
            String resourceType,
            String resourceId,
            LogLevel level,
            String message,
            String additionalData,
            String ipAddress,
            String userAgent,
            LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.username = username;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.level = level;
        this.message = message;
        this.additionalData = additionalData;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.createdAt = createdAt;
    }
}
