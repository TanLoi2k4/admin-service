package com.tlcn.admin_service.controller;

import com.tlcn.admin_service.dto.LogResponse;
import com.tlcn.admin_service.dto.ResponseDTO;
import com.tlcn.admin_service.enums.LogLevel;
import com.tlcn.admin_service.service.LogService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/admin/logs")
public class LogController {

    private final LogService logService;

    public LogController(LogService logService) {
        this.logService = logService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<ResponseDTO<Page<LogResponse>>> getLogs(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "userId", required = false) Long userId,
            @RequestParam(name = "action", required = false) String action,
            @RequestParam(name = "resourceType", required = false) String resourceType,
            @RequestParam(name = "level", required = false) LogLevel level,
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        Pageable pageable = PageRequest.of(page, size);
        Page<LogResponse> logs = logService.getLogsWithFilters(pageable, userId, action, resourceType, level, startDate, endDate);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Logs retrieved successfully", logs));
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<ResponseDTO<Page<LogResponse>>> getLogsByUser(
            @PathVariable("userId") Long userId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<LogResponse> logs = logService.getLogsByUser(userId, pageable);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Logs by user retrieved successfully", logs));
    }

    @GetMapping("/action/{action}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<ResponseDTO<Page<LogResponse>>> getLogsByAction(
            @PathVariable("action") String action,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<LogResponse> logs = logService.getLogsByAction(action, pageable);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Logs by action retrieved successfully", logs));
    }

    @GetMapping("/resource/{resourceType}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<ResponseDTO<Page<LogResponse>>> getLogsByResourceType(
            @PathVariable("resourceType") String resourceType,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<LogResponse> logs = logService.getLogsByResourceType(resourceType, pageable);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Logs by resource type retrieved successfully", logs));
    }

    @GetMapping("/level/{level}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<ResponseDTO<Page<LogResponse>>> getLogsByLevel(
            @PathVariable("level") LogLevel level,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<LogResponse> logs = logService.getLogsByLevel(level, pageable);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Logs by level retrieved successfully", logs));
    }

    @GetMapping("/daterange")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<ResponseDTO<Page<LogResponse>>> getLogsByDateRange(
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<LogResponse> logs = logService.getLogsByDateRange(startDate, endDate, pageable);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Logs by date range retrieved successfully", logs));
    }

    @GetMapping("/recent/{userId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<ResponseDTO<java.util.List<LogResponse>>> getRecentLogsByUser(
            @PathVariable("userId") Long userId,
            @RequestParam(name = "hours", defaultValue = "24") int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        var logs = logService.getRecentLogsByUser(userId, since);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Recent logs retrieved successfully", logs));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<ResponseDTO<java.util.Map<String, Object>>> getLogStats(
            @RequestParam(name = "hours", defaultValue = "24") int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        var stats = logService.getLogStats(since);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Log statistics retrieved successfully", stats));
    }
}