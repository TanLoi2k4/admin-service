package com.tlcn.admin_service.service;

import com.tlcn.admin_service.dto.ActiveAdminResponse;
import com.tlcn.admin_service.dto.AdminUserCreateRequest;
import com.tlcn.admin_service.dto.AdminUserUpdateAccountRequest;
import com.tlcn.admin_service.dto.AdminUserResponse;
import com.tlcn.admin_service.dto.AdminUpdateAccountRequest;
import com.tlcn.admin_service.dto.AdminUpdateProfileRequest;
import com.tlcn.admin_service.dto.ForgetPasswordRequest;
import com.tlcn.admin_service.dto.LoginRequest;
import com.tlcn.admin_service.dto.LoginResponse;
import com.tlcn.admin_service.dto.ResetPasswordRequest;
import com.tlcn.admin_service.enums.AdminUserStatus;
import com.tlcn.admin_service.enums.LogLevel;
import com.tlcn.admin_service.exception.BusinessException;
import com.tlcn.admin_service.exception.ResourceNotFoundException;
import com.tlcn.admin_service.exception.UnauthorizedException;
import com.tlcn.admin_service.model.AdminUser;
import com.tlcn.admin_service.repository.AdminUserRepository;
import lombok.extern.slf4j.Slf4j;

import org.keycloak.representations.AccessTokenResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class AdminUserService {

    private final AdminUserRepository adminUserRepository;
    private final KeycloakService keycloakService;
    private final EmailService emailService;
    private final LogService logService;
    private final TokenService tokenService;

    public AdminUserService(
            AdminUserRepository adminUserRepository,
            KeycloakService keycloakService,
            EmailService emailService,
            LogService logService,
            TokenService tokenService) {
        this.adminUserRepository = adminUserRepository;
        this.keycloakService = keycloakService;
        this.emailService = emailService;
        this.logService = logService;
        this.tokenService = tokenService;
    }
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    /**
     * Create new admin user
     */
    @Transactional
    public AdminUserResponse createAdminUser(AdminUserCreateRequest request) {
        log.info("Creating new admin user: username={}, email={}", request.getUsername(), request.getEmail());

        // Check if username or email already exists
        if (adminUserRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("Username already exists");
        }
        if (adminUserRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Email already exists");
        }

        // Check Keycloak
        if (keycloakService.userExists(request.getUsername()) || keycloakService.emailExists(request.getEmail())) {
            throw new BusinessException("Username or email already exists in Keycloak");
        }

        try {
            String passwordToSet = request.getPassword();

            // Create user in Keycloak
            String keycloakId = keycloakService.createAdminUser(
                request.getUsername(),
                request.getEmail(),
                request.getFirstName(),
                request.getLastName(),
                passwordToSet
            );

            // Create user in database
            AdminUser adminUser = new AdminUser();
            adminUser.setUsername(request.getUsername());
            adminUser.setEmail(request.getEmail());
            adminUser.setFirstName(request.getFirstName());
            adminUser.setLastName(request.getLastName());
            adminUser.setPhoneNumber(request.getPhoneNumber());
            adminUser.setKeycloakId(keycloakId);
            adminUser.setStatus(AdminUserStatus.ACTIVE);

            AdminUser savedUser = adminUserRepository.save(adminUser);

            try {
                emailService.sendAdminAccountCreated(
                    request.getEmail(),
                    request.getLastName() + " " + request.getFirstName(),
                    request.getUsername()
                );
            } catch (Exception emailError) {
                log.warn("Admin user created but notification email failed for {}: {}",
                        request.getUsername(), emailError.getMessage());
            }

            try {
                logService.logAction(
                    getCurrentUserId(),
                    getCurrentUsername(),
                    "CREATE_ADMIN_USER",
                    "ADMIN_USER",
                    savedUser.getId().toString(),
                    LogLevel.INFO,
                    "Created new admin user: " + request.getUsername(),
                    null,
                    null,
                    null
                );
            } catch (Exception logError) {
                log.warn("Admin user created but audit log failed for {}: {}",
                        request.getUsername(), logError.getMessage());
            }

            log.info("Admin user created successfully: id={}, username={}", savedUser.getId(), savedUser.getUsername());
            return convertToDTO(savedUser);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to create admin user: {}", e.getMessage(), e);
            throw new BusinessException("Failed to create admin user: " + e.getMessage());
        }
    }

    /**
     * Get all admin users with pagination and filtering
     */
    public Page<AdminUserResponse> getAllAdminUsers(Pageable pageable, AdminUserStatus status, String search) {
        log.debug("Getting admin users: status={}, search={}, page={}, size={}",
                 status, search, pageable.getPageNumber(), pageable.getPageSize());

        Page<AdminUser> users;

        if (status != null && search != null && !search.isBlank()) {
            users = adminUserRepository.findByStatusAndUsernameContainingIgnoreCase(status, search, pageable);
        } else if (status != null) {
            users = adminUserRepository.findByStatus(status, pageable);
        } else if (search != null && !search.isBlank()) {
            users = adminUserRepository.findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(search, pageable);
        } else {
            users = adminUserRepository.findAll(pageable);
        }

        return users.map(this::convertToDTO);
    }

    /**
     * Get admin user by ID
     */
    public AdminUserResponse getAdminUserById(Long id) {
        log.debug("Getting admin user by ID: {}", id);

        AdminUser adminUser = adminUserRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Admin User", id.toString()));

        return convertToDTO(adminUser);
    }

    /**
     * Update current admin user's profile (self-service).
     * Only SUPER_ADMIN may update personal info; regular ADMIN cannot.
     */
    @Transactional
    public AdminUserResponse updateMyProfile(AdminUpdateProfileRequest request) {
        if (!isCurrentUserSuperAdmin()) {
            throw new UnauthorizedException("Only SUPER_ADMIN can update personal information");
        }

        String username = getCurrentUsername();
        log.info("Updating my profile: username={}", username);

        AdminUser adminUser = adminUserRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Admin User", username));

        boolean hasChanges = false;
        if (request.getFirstName() != null && !request.getFirstName().equals(adminUser.getFirstName())) hasChanges = true;
        if (request.getLastName() != null && !request.getLastName().equals(adminUser.getLastName())) hasChanges = true;
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().equals(adminUser.getPhoneNumber())) hasChanges = true;

        if (!hasChanges) return convertToDTO(adminUser);

        try {
            String newFirstName = request.getFirstName() != null ? request.getFirstName() : adminUser.getFirstName();
            String newLastName = request.getLastName() != null ? request.getLastName() : adminUser.getLastName();

            if ((request.getFirstName() != null && !request.getFirstName().equals(adminUser.getFirstName())) ||
                (request.getLastName() != null && !request.getLastName().equals(adminUser.getLastName()))) {
                keycloakService.updateAdminUser(
                    adminUser.getKeycloakId(),
                    adminUser.getUsername(),
                    adminUser.getEmail(),
                    newFirstName,
                    newLastName
                );
            }

            if (request.getFirstName() != null) adminUser.setFirstName(request.getFirstName());
            if (request.getLastName() != null) adminUser.setLastName(request.getLastName());
            if (request.getPhoneNumber() != null) adminUser.setPhoneNumber(request.getPhoneNumber());
            adminUser.setUpdatedAt(LocalDateTime.now());

            AdminUser savedUser = adminUserRepository.save(adminUser);

            logService.logAction(
                savedUser.getId(),
                savedUser.getUsername(),
                "UPDATE_MY_PROFILE",
                "ADMIN_USER",
                savedUser.getId().toString(),
                LogLevel.INFO,
                "Updated my profile",
                null,
                null,
                null
            );

            return convertToDTO(savedUser);
        } catch (Exception e) {
            log.error("Failed to update my profile: {}", e.getMessage(), e);
            throw new BusinessException("Failed to update profile: " + e.getMessage());
        }
    }

    /**
     * Update current admin user's account (self-service).
     * Regular ADMIN may only change username and password.
     * SUPER_ADMIN may also change email.
     */
    @Transactional
    public AdminUserResponse updateMyAccount(AdminUpdateAccountRequest request) {
        String username = getCurrentUsername();
        log.info("Updating my account: username={}", username);

        AdminUser adminUser = adminUserRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Admin User", username));

        verifyCurrentPassword(adminUser.getUsername(), request.getCurrentPassword());

        boolean isSuperAdmin = isCurrentUserSuperAdmin();
        boolean emailChanging = request.getEmail() != null && !request.getEmail().equals(adminUser.getEmail());
        if (!isSuperAdmin && emailChanging) {
            throw new UnauthorizedException("Only SUPER_ADMIN can update email. ADMIN may only update username and password");
        }

        boolean hasChanges = false;
        if (request.getUsername() != null && !request.getUsername().equals(adminUser.getUsername())) hasChanges = true;
        if (isSuperAdmin && emailChanging) hasChanges = true;
        if (request.getPassword() != null && !request.getPassword().isBlank()) hasChanges = true;

        if (!hasChanges) return convertToDTO(adminUser);

        try {
            if (request.getUsername() != null && !request.getUsername().equals(adminUser.getUsername())) {
                if (adminUserRepository.existsByUsername(request.getUsername())) {
                    throw new BusinessException("Username already exists");
                }
                if (keycloakService.userExists(request.getUsername())) {
                    throw new BusinessException("Username already exists in Keycloak");
                }
                adminUser.setUsername(request.getUsername());
            }

            if (isSuperAdmin && emailChanging) {
                if (adminUserRepository.existsByEmail(request.getEmail())) {
                    throw new BusinessException("Email already exists");
                }
                if (keycloakService.emailExists(request.getEmail())) {
                    throw new BusinessException("Email already exists in Keycloak");
                }
                adminUser.setEmail(request.getEmail());
            }

            keycloakService.updateAdminUser(
                adminUser.getKeycloakId(),
                adminUser.getUsername(),
                adminUser.getEmail(),
                adminUser.getFirstName(),
                adminUser.getLastName()
            );

            if (request.getPassword() != null && !request.getPassword().isBlank()) {
                validatePassword(request.getPassword());
                keycloakService.changePassword(adminUser.getKeycloakId(), request.getPassword());
            }

            adminUser.setUpdatedAt(LocalDateTime.now());
            AdminUser savedUser = adminUserRepository.save(adminUser);

            logService.logAction(
                savedUser.getId(),
                savedUser.getUsername(),
                "UPDATE_MY_ACCOUNT",
                "ADMIN_USER",
                savedUser.getId().toString(),
                LogLevel.INFO,
                "Updated my account",
                null,
                null,
                null
            );

            return convertToDTO(savedUser);
        } catch (Exception e) {
            log.error("Failed to update my account: {}", e.getMessage(), e);
            throw new BusinessException("Failed to update account: " + e.getMessage());
        }
    }

    /**
     * SUPER_ADMIN: update another admin's profile by id
     */
    @Transactional
    public AdminUserResponse updateAdminProfile(Long id, AdminUpdateProfileRequest request) {
        log.info("Updating admin profile: id={}", id);

        AdminUser adminUser = adminUserRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Admin User", id.toString()));

        boolean hasChanges = false;
        if (request.getFirstName() != null && !request.getFirstName().equals(adminUser.getFirstName())) hasChanges = true;
        if (request.getLastName() != null && !request.getLastName().equals(adminUser.getLastName())) hasChanges = true;
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().equals(adminUser.getPhoneNumber())) hasChanges = true;
        if (!hasChanges) return convertToDTO(adminUser);

        try {
            String newFirstName = request.getFirstName() != null ? request.getFirstName() : adminUser.getFirstName();
            String newLastName = request.getLastName() != null ? request.getLastName() : adminUser.getLastName();

            if ((request.getFirstName() != null && !request.getFirstName().equals(adminUser.getFirstName())) ||
                (request.getLastName() != null && !request.getLastName().equals(adminUser.getLastName()))) {
                keycloakService.updateAdminUser(
                    adminUser.getKeycloakId(),
                    adminUser.getUsername(),
                    adminUser.getEmail(),
                    newFirstName,
                    newLastName
                );
            }

            if (request.getFirstName() != null) adminUser.setFirstName(request.getFirstName());
            if (request.getLastName() != null) adminUser.setLastName(request.getLastName());
            if (request.getPhoneNumber() != null) adminUser.setPhoneNumber(request.getPhoneNumber());
            adminUser.setUpdatedAt(LocalDateTime.now());

            AdminUser savedUser = adminUserRepository.save(adminUser);

            logService.logAction(
                getCurrentUserId(),
                getCurrentUsername(),
                "UPDATE_ADMIN_PROFILE",
                "ADMIN_USER",
                id.toString(),
                LogLevel.INFO,
                "Updated admin profile: " + adminUser.getUsername(),
                null,
                null,
                null
            );

            return convertToDTO(savedUser);
        } catch (Exception e) {
            log.error("Failed to update admin profile: {}", e.getMessage(), e);
            throw new BusinessException("Failed to update admin profile: " + e.getMessage());
        }
    }

    /**
     * SUPER_ADMIN: update another admin's account by id (email/password/status)
     */
    @Transactional
    public AdminUserResponse updateAdminAccount(Long id, AdminUserUpdateAccountRequest request) {
        log.info("Updating admin account: id={}", id);

        AdminUser adminUser = adminUserRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Admin User", id.toString()));

        boolean hasChanges = false;
        if (request.getEmail() != null && !request.getEmail().equals(adminUser.getEmail())) hasChanges = true;
        if (request.getPassword() != null && !request.getPassword().isBlank()) hasChanges = true;
        if (request.getStatus() != null && !request.getStatus().equals(adminUser.getStatus())) hasChanges = true;
        if (!hasChanges) return convertToDTO(adminUser);

        try {
            if (request.getEmail() != null && !request.getEmail().equals(adminUser.getEmail())) {
                if (adminUserRepository.existsByEmail(request.getEmail())) {
                    throw new BusinessException("Email already exists");
                }
                if (keycloakService.emailExists(request.getEmail())) {
                    throw new BusinessException("Email already exists in Keycloak");
                }
                adminUser.setEmail(request.getEmail());
            }

            if (request.getStatus() != null && !request.getStatus().equals(adminUser.getStatus())) {
                AdminUserStatus oldStatus = adminUser.getStatus();
                adminUser.setStatus(request.getStatus());
                if (!request.getStatus().equals(oldStatus)) {
                    boolean enabled = request.getStatus() == AdminUserStatus.ACTIVE;
                    keycloakService.setAdminUserEnabled(adminUser.getKeycloakId(), enabled);
                }
            }

            keycloakService.updateAdminUser(
                adminUser.getKeycloakId(),
                adminUser.getUsername(),
                adminUser.getEmail(),
                adminUser.getFirstName(),
                adminUser.getLastName()
            );

            if (request.getPassword() != null && !request.getPassword().isBlank()) {
                validatePassword(request.getPassword());
                keycloakService.changePassword(adminUser.getKeycloakId(), request.getPassword());
            }

            adminUser.setUpdatedAt(LocalDateTime.now());
            AdminUser savedUser = adminUserRepository.save(adminUser);

            logService.logAction(
                getCurrentUserId(),
                getCurrentUsername(),
                "UPDATE_ADMIN_ACCOUNT",
                "ADMIN_USER",
                id.toString(),
                LogLevel.INFO,
                "Updated admin account: " + adminUser.getUsername(),
                null,
                null,
                null
            );

            return convertToDTO(savedUser);
        } catch (Exception e) {
            log.error("Failed to update admin account: {}", e.getMessage(), e);
            throw new BusinessException("Failed to update admin account: " + e.getMessage());
        }
    }

    /**
     * Update admin user status
     */
    @Transactional
    public AdminUserResponse updateAdminUserStatus(Long id, AdminUserStatus newStatus) {
        log.info("Updating admin user status: id={}, newStatus={}", id, newStatus);

        AdminUser adminUser = adminUserRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Admin User", id.toString()));

        AdminUserStatus oldStatus = adminUser.getStatus();

        // Validate status transition
        if (!isValidStatusTransition(oldStatus, newStatus)) {
            throw new BusinessException("Invalid status transition from " + oldStatus + " to " + newStatus);
        }

        try {
            // Update in Keycloak (enable/disable)
            boolean enabled = newStatus == AdminUserStatus.ACTIVE;
            keycloakService.setAdminUserEnabled(adminUser.getKeycloakId(), enabled);

            // Update in database
            adminUser.setStatus(newStatus);
            adminUser.setUpdatedAt(LocalDateTime.now());

            AdminUser savedUser = adminUserRepository.save(adminUser);

            // Send notification email
            emailService.sendAdminStatusChanged(
                adminUser.getEmail(),
                adminUser.getLastName() + " " + adminUser.getFirstName(),
                newStatus.toString()
            );

            // Log the action
            logService.logAction(
                getCurrentUserId(),
                getCurrentUsername(),
                "UPDATE_ADMIN_USER_STATUS",
                "ADMIN_USER",
                id.toString(),
                LogLevel.INFO,
                "Updated admin user status: " + adminUser.getUsername() + " from " + oldStatus + " to " + newStatus,
                null,
                null,
                null
            );

            log.info("Admin user status updated: id={}, {} -> {}", id, oldStatus, newStatus);
            return convertToDTO(savedUser);

        } catch (Exception e) {
            log.error("Failed to update admin user status: {}", e.getMessage(), e);
            throw new BusinessException("Failed to update admin user status: " + e.getMessage());
        }
    }

    /**
     * Delete admin user
     */
    @Transactional
    public void deleteAdminUser(Long id) {
        log.info("Deleting admin user: id={}", id);

        AdminUser adminUser = adminUserRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Admin User", id.toString()));

        try {
            // Delete from Keycloak
            keycloakService.deleteAdminUser(adminUser.getKeycloakId());

            // Delete from database
            adminUserRepository.delete(adminUser);

            // Log the action
            logService.logAction(
                getCurrentUserId(),
                getCurrentUsername(),
                "DELETE_ADMIN_USER",
                "ADMIN_USER",
                id.toString(),
                LogLevel.INFO,
                "Deleted admin user: " + adminUser.getUsername(),
                null,
                null,
                null
            );

            log.info("Admin user deleted successfully: id={}", id);

        } catch (Exception e) {
            log.error("Failed to delete admin user: {}", e.getMessage(), e);
            throw new BusinessException("Failed to delete admin user: " + e.getMessage());
        }
    }

    /**
     * Change admin user password
     */
    @Transactional
    public void changePassword(Long id, String newPassword) {
        log.info("Changing password for admin user: id={}", id);

        if (!isCurrentUserSuperAdmin() && !id.equals(getCurrentUserId())) {
            throw new UnauthorizedException("You can only change your own password");
        }

        AdminUser adminUser = adminUserRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Admin User", id.toString()));

        // Validate new password
        validatePassword(newPassword);

        try {
            // Update password in Keycloak
            keycloakService.changePassword(adminUser.getKeycloakId(), newPassword);

            // Log the action
            logService.logAction(
                getCurrentUserId(),
                getCurrentUsername(),
                "CHANGE_ADMIN_PASSWORD",
                "ADMIN_USER",
                id.toString(),
                LogLevel.INFO,
                "Changed password for admin user: " + adminUser.getUsername(),
                null,
                null,
                null
            );

            log.info("Password changed successfully for admin user: id={}", id);

        } catch (Exception e) {
            log.error("Failed to change password for admin user: {}", e.getMessage(), e);
            throw new BusinessException("Failed to change password: " + e.getMessage());
        }
    }

    /**
     * Validate status transition
     */
    private boolean isValidStatusTransition(AdminUserStatus oldStatus, AdminUserStatus newStatus) {
        // ACTIVE -> INACTIVE, SUSPENDED
        if (oldStatus == AdminUserStatus.ACTIVE) {
            return newStatus == AdminUserStatus.INACTIVE || newStatus == AdminUserStatus.SUSPENDED;
        }
        // INACTIVE -> ACTIVE
        else if (oldStatus == AdminUserStatus.INACTIVE) {
            return newStatus == AdminUserStatus.ACTIVE;
        }
        // SUSPENDED -> ACTIVE
        else if (oldStatus == AdminUserStatus.SUSPENDED) {
            return newStatus == AdminUserStatus.ACTIVE;
        }
        return false;
    }

    /**
     * Validate password strength
     */
    private void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new BusinessException("Password must be at least 8 characters long");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new BusinessException("Password must contain at least one uppercase letter");
        }
        if (!password.matches(".*[a-z].*")) {
            throw new BusinessException("Password must contain at least one lowercase letter");
        }
        if (!password.matches(".*\\d.*")) {
            throw new BusinessException("Password must contain at least one digit");
        }
    }

    private void verifyCurrentPassword(String username, String currentPassword) {
        try {
            keycloakService.getUserToken(username, currentPassword);
        } catch (Exception e) {
            throw new BusinessException("Current password incorrect");
        }
    }

    private boolean isCurrentUserSuperAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) return false;
        return authentication.getAuthorities().stream().anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()));
    }

    /**
     * Get current admin user profile
     */
    public AdminUserResponse getCurrentAdminUser() {
        String username = getCurrentUsername();
        log.debug("Getting current admin user: {}", username);

        AdminUser adminUser = adminUserRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Admin User", username));

        return convertToDTO(adminUser);
    }

    /**
     * Get current username from security context
     */
    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new UnauthorizedException("No authenticated user found");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            String preferredUsername = jwt.getClaimAsString("preferred_username");
            if (preferredUsername != null && !preferredUsername.isBlank()) {
                return preferredUsername;
            }
        }

        String name = authentication.getName();
        if (name != null && !name.isBlank()) {
            return name;
        }

        throw new UnauthorizedException("No authenticated user found");
    }

    /**
     * Get current user ID
     */
    private Long getCurrentUserId() {
        String username = getCurrentUsername();
        AdminUser adminUser = adminUserRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Admin User", username));
        return adminUser.getId();
    }

    /**
     * Login admin user
     */
    public LoginResponse login(LoginRequest request) {
        log.info("Admin user login attempt: username={}", request.getUsername());

        try {
            // Get token from Keycloak
            var tokenResponse = keycloakService.getUserToken(request.getUsername(), request.getPassword());

            // Check if user exists in our database
            AdminUser adminUser = adminUserRepository.findByUsername(request.getUsername())
                    .orElseThrow(() -> new ResourceNotFoundException("Admin User", request.getUsername()));

            // Check if user is active
            if (adminUser.getStatus() != AdminUserStatus.ACTIVE) {
                throw new BusinessException("Account is not active. Current status: " + adminUser.getStatus());
            }

            // Update last login time
            adminUser.setLastLoginAt(LocalDateTime.now());
            adminUserRepository.save(adminUser);

            // Log the login action
            logService.logAction(
                adminUser.getId(),
                adminUser.getUsername(),
                "LOGIN",
                "ADMIN_USER",
                adminUser.getId().toString(),
                LogLevel.INFO,
                "Admin user logged in successfully",
                null,
                null,
                null
            );

            log.info("Admin user logged in successfully: username={}", request.getUsername());

            return new LoginResponse(
                    tokenResponse.getToken(),
                    tokenResponse.getRefreshToken(),
                    tokenResponse.getTokenType(),
                    tokenResponse.getExpiresIn(),
                    tokenResponse.getScope()
            );

        } catch (Exception e) {
    log.error("Login failed", e);
    throw e;
}
    }

    /**
     * Refresh access token using refresh token.
     */
    public LoginResponse refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException("Refresh token is required");
        }

        try {
            AccessTokenResponse tokenResponse = keycloakService.refreshToken(refreshToken);
            return new LoginResponse(
                    tokenResponse.getToken(),
                    tokenResponse.getRefreshToken(),
                    tokenResponse.getTokenType(),
                    tokenResponse.getExpiresIn(),
                    tokenResponse.getScope()
            );
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Refresh failed: {}", e.getMessage());
            throw new BusinessException("Invalid or expired refresh token");
        }
    }

    /**
     * Logout admin user
     */
    public void logout(String accessToken, String refreshToken) {
        if (accessToken == null) {
            log.warn("Access token is missing");
            throw new BusinessException("Access token is required");
        }

        String username = getCurrentUsername();
        log.info("Admin user logout: username={}", username);

        try {
            // Get current user
            AdminUser adminUser = adminUserRepository.findByUsername(username)
                    .orElseThrow(() -> new ResourceNotFoundException("Admin User", username));

            // Blacklist the token
            tokenService.blacklistToken(accessToken, 300); // 5 minutes

            // If refreshToken is present, logout from Keycloak
            if (refreshToken != null) {
                try {
                    keycloakService.logoutUser(accessToken, refreshToken);
                } catch (Exception e) {
                    log.warn("Keycloak logout failed: {}", e.getMessage());
                    // Don't fail whole request because of Keycloak logout problem
                }
            }

            // Log the logout action
            logService.logAction(
                adminUser.getId(),
                adminUser.getUsername(),
                "LOGOUT",
                "ADMIN_USER",
                adminUser.getId().toString(),
                LogLevel.INFO,
                "Admin user logged out successfully",
                null,
                null,
                null
            );

            log.info("Admin user logged out successfully: username={}", username);

        } catch (Exception e) {
            log.error("Error during logout for username: {}", username, e);
            throw new BusinessException("Logout failed: " + e.getMessage());
        }
    }

    /**
     * Forget password - send reset link
     */
    public void forgetPassword(ForgetPasswordRequest request) {
        log.info("Password reset requested for email: {}", request.getEmail());

        try {
            AdminUser adminUser = adminUserRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new ResourceNotFoundException("Admin User", request.getEmail()));

            if (adminUser.getStatus() != AdminUserStatus.ACTIVE) {
                throw new BusinessException("Account is not active. Cannot reset password.");
            }

            String resetToken = UUID.randomUUID().toString();
            tokenService.storeResetToken(resetToken, request.getEmail());

            emailService.sendPasswordResetEmail(
                adminUser.getEmail(),
                adminUser.getLastName() + " " + adminUser.getFirstName(),
                resetToken
            );

            logService.logAction(
                adminUser.getId(),
                adminUser.getUsername(),
                "FORGET_PASSWORD",
                "ADMIN_USER",
                adminUser.getId().toString(),
                LogLevel.INFO,
                "Password reset link sent to: " + adminUser.getEmail(),
                null,
                null,
                null
            );

            log.info("Password reset link sent successfully to: {}", adminUser.getEmail());

        } catch (ResourceNotFoundException e) {
            log.warn("Password reset attempted for non-existent email: {}", request.getEmail());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error during password reset request for email: {}", request.getEmail(), e);
            throw new BusinessException("Failed to process password reset request");
        }
    }

    /**
     * Reset password using token from email link
     */
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        log.info("Password reset attempt using token");

        try {
            validatePassword(request.getPassword());

            String email = tokenService.getResetTokenEmail(request.getToken());
            if (email == null) {
                throw new BusinessException("Invalid or expired reset token");
            }

            AdminUser adminUser = adminUserRepository.findByEmail(email)
                    .orElseThrow(() -> new ResourceNotFoundException("Admin User", email));

            keycloakService.changePassword(adminUser.getKeycloakId(), request.getPassword());

            adminUser.setUpdatedAt(LocalDateTime.now());
            adminUserRepository.save(adminUser);

            tokenService.removeResetToken(request.getToken());

            emailService.sendPasswordChangedNotification(
                adminUser.getEmail(),
                adminUser.getLastName() + " " + adminUser.getFirstName()
            );

            logService.logAction(
                adminUser.getId(),
                adminUser.getUsername(),
                "RESET_PASSWORD",
                "ADMIN_USER",
                adminUser.getId().toString(),
                LogLevel.INFO,
                "Password reset completed for admin user",
                null,
                null,
                null
            );

            log.info("Password reset completed successfully for admin user: {}", adminUser.getEmail());

        } catch (ResourceNotFoundException e) {
            throw new BusinessException("Admin user not found");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error during password reset", e);
            throw new BusinessException("Failed to reset password: " + e.getMessage());
        }
    }

    private String generateOtp() {
        return String.format("%06d", (int) (Math.random() * 1000000));
    }

    /**
     * Admin profile lookup by keycloakId for internal service-to-service calls
     * (e.g. communication-service resolving the display name of an assigned admin).
     */
    public ActiveAdminResponse getAdminProfileByKeycloakId(String keycloakId) {
        return adminUserRepository.findByKeycloakId(keycloakId)
                .map(admin -> new ActiveAdminResponse(
                        admin.getKeycloakId(),
                        admin.getLastName() + " " + admin.getFirstName(),
                        admin.getEmail()))
                .orElse(null);
    }

    /**
     * Active admins for internal service assignment (communication-service).
     */
    public List<ActiveAdminResponse> getActiveAdmins() {
        return adminUserRepository.findByStatus(AdminUserStatus.ACTIVE, Pageable.unpaged())
                .stream()
                .filter(admin -> admin.getKeycloakId() != null && !admin.getKeycloakId().isBlank())
                .map(admin -> new ActiveAdminResponse(
                        admin.getKeycloakId(),
                        admin.getLastName() + " " + admin.getFirstName(),
                        admin.getEmail()))
                .toList();
    }

    /**
     * Convert AdminUser to AdminUserDTO
     */
    private AdminUserResponse convertToDTO(AdminUser adminUser) {
        return new AdminUserResponse(
                adminUser.getId(),
                adminUser.getUsername(),
                adminUser.getEmail(),
                adminUser.getFirstName(),
                adminUser.getLastName(),
                adminUser.getPhoneNumber(),
                adminUser.getStatus(),
                adminUser.getKeycloakId(),
                adminUser.getCreatedAt(),
                adminUser.getUpdatedAt(),
                adminUser.getCreatedBy(),
                adminUser.getUpdatedBy()
        );
    }
}