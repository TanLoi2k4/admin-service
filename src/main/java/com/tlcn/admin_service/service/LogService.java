package com.tlcn.admin_service.service;

import com.tlcn.admin_service.dto.LogResponse;
import com.tlcn.admin_service.enums.LogLevel;
import com.tlcn.admin_service.model.Log;
import com.tlcn.admin_service.repository.LogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional(readOnly = true)
public class LogService {

    private final LogRepository logRepository;

    public LogService(LogRepository logRepository) {
        this.logRepository = logRepository;
    }

    public Page<LogResponse> getLogsWithFilters(Pageable pageable, Long userId, String action,
                                          String resourceType, LogLevel level,
                                          LocalDateTime startDate, LocalDateTime endDate) {
        Page<Log> logs = logRepository.findLogsWithFilters(userId, action, resourceType, level, startDate, endDate, pageable);
        return logs.map(this::convertToDTO);
    }

    public Page<LogResponse> getLogsByUser(Long userId, Pageable pageable) {
        Page<Log> logs = logRepository.findByUserIdOrderByTimestampDesc(userId, pageable);
        return logs.map(this::convertToDTO);
    }

    public Page<LogResponse> getLogsByAction(String action, Pageable pageable) {
        Page<Log> logs = logRepository.findByActionOrderByTimestampDesc(action, pageable);
        return logs.map(this::convertToDTO);
    }

    public Page<LogResponse> getLogsByResourceType(String resourceType, Pageable pageable) {
        Page<Log> logs = logRepository.findByResourceTypeOrderByTimestampDesc(resourceType, pageable);
        return logs.map(this::convertToDTO);
    }

    public Page<LogResponse> getLogsByLevel(LogLevel level, Pageable pageable) {
        Page<Log> logs = logRepository.findByLevel(level, pageable);
        return logs.map(this::convertToDTO);
    }

    public Page<LogResponse> getLogsByDateRange(LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        Page<Log> logs = logRepository.findByTimestampBetween(startDate, endDate, pageable);
        return logs.map(this::convertToDTO);
    }

    public List<LogResponse> getRecentLogsByUser(Long userId, LocalDateTime since) {
        List<Log> logs = logRepository.findRecentLogsByUser(userId, since);
        return logs.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    public Map<String, Object> getLogStats(LocalDateTime since) {
        long totalLogs = logRepository.count();
        long recentLogs = logRepository.countByTimestampGreaterThanEqual(since);

        Map<LogLevel, Long> logsByLevel = Map.of(
            LogLevel.INFO, logRepository.countLogsByLevelSince(LogLevel.INFO, since),
            LogLevel.WARN, logRepository.countLogsByLevelSince(LogLevel.WARN, since),
            LogLevel.ERROR, logRepository.countLogsByLevelSince(LogLevel.ERROR, since),
            LogLevel.DEBUG, logRepository.countLogsByLevelSince(LogLevel.DEBUG, since)
        );

        return Map.of(
            "totalLogs", totalLogs,
            "recentLogs", recentLogs,
            "logsByLevel", logsByLevel,
            "since", since
        );
    }

    @Transactional
    public void logAction(Long userId, String username, String action, String resourceType,
                         String resourceId, LogLevel level, String message,
                         String additionalData, String ipAddress, String userAgent) {
        Log logEntry = new Log();
        logEntry.setUserId(userId);
        logEntry.setUsername(username);
        logEntry.setAction(action);
        logEntry.setResourceType(resourceType);
        logEntry.setResourceId(resourceId);
        logEntry.setLevel(level);
        logEntry.setMessage(message);
        logEntry.setAdditionalData(additionalData);
        logEntry.setIpAddress(ipAddress);
        logEntry.setUserAgent(userAgent);

        logRepository.save(logEntry);
        log.info("Logged action: {} for user: {} on resource: {}", action, username, resourceType);
    }

    private LogResponse convertToDTO(Log log) {
        return new LogResponse(
                log.getId(),
                log.getUserId(),
                log.getUsername(),
                log.getAction(),
                log.getResourceType(),
                log.getResourceId(),
                log.getLevel(),
                log.getMessage(),
                log.getAdditionalData(),
                log.getIpAddress(),
                log.getUserAgent(),
                log.getTimestamp()
        );
    }
}