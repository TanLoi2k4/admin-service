package com.tlcn.admin_service.repository;

import com.tlcn.admin_service.enums.LogLevel;
import com.tlcn.admin_service.model.Log;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LogRepository extends JpaRepository<Log, Long> {

    Page<Log> findByUserIdOrderByTimestampDesc(Long userId, Pageable pageable);

    Page<Log> findByActionOrderByTimestampDesc(String action, Pageable pageable);

    Page<Log> findByResourceTypeOrderByTimestampDesc(String resourceType, Pageable pageable);

    Page<Log> findByLevel(LogLevel level, Pageable pageable);

    Page<Log> findByTimestampBetween(
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable
    );

    @Query("""
        SELECT l
        FROM Log l
        WHERE (CAST(:userId AS long) IS NULL OR l.userId = :userId)
          AND (CAST(:action AS string) IS NULL OR l.action = :action)
          AND (CAST(:resourceType AS string) IS NULL OR l.resourceType = :resourceType)
          AND (CAST(:level AS string) IS NULL OR l.level = :level)
          AND (CAST(:startDate AS localdatetime) IS NULL OR l.timestamp >= :startDate)
          AND (CAST(:endDate AS localdatetime) IS NULL OR l.timestamp <= :endDate)
        ORDER BY l.timestamp DESC
    """)
    Page<Log> findLogsWithFilters(
            @Param("userId") Long userId,
            @Param("action") String action,
            @Param("resourceType") String resourceType,
            @Param("level") LogLevel level,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );

    @Query("""
        SELECT l
        FROM Log l
        WHERE l.userId = :userId
          AND l.timestamp >= :since
        ORDER BY l.timestamp DESC
    """)
    List<Log> findRecentLogsByUser(
            @Param("userId") Long userId,
            @Param("since") LocalDateTime since
    );

    @Query("""
        SELECT COUNT(l)
        FROM Log l
        WHERE l.level = :level
          AND l.timestamp >= :since
    """)
    long countLogsByLevelSince(
            @Param("level") LogLevel level,
            @Param("since") LocalDateTime since
    );

    long countByTimestampGreaterThanEqual(LocalDateTime since);
}