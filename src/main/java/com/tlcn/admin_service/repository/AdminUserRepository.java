package com.tlcn.admin_service.repository;

import com.tlcn.admin_service.enums.AdminUserStatus;
import com.tlcn.admin_service.model.AdminUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Optional<AdminUser> findByUsername(String username);

    Optional<AdminUser> findByEmail(String email);

    Optional<AdminUser> findByKeycloakId(String keycloakId);

    Page<AdminUser> findByStatus(AdminUserStatus status, Pageable pageable);

    Page<AdminUser> findByUsernameContainingIgnoreCase(String username, Pageable pageable);

    Page<AdminUser> findByStatusAndUsernameContainingIgnoreCase(AdminUserStatus status, String username, Pageable pageable);

    @Query("SELECT COUNT(a) FROM AdminUser a WHERE a.status = :status")
    long countByStatus(@Param("status") AdminUserStatus status);

    @Query("SELECT a FROM AdminUser a WHERE " +
           "(LOWER(a.username) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(a.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<AdminUser> findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(
           @Param("search") String search,
            Pageable pageable);
}