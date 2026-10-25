package com.tlcn.admin_service.controller;

import com.tlcn.admin_service.dto.ActiveAdminResponse;
import com.tlcn.admin_service.dto.AdminUserCreateRequest;
import com.tlcn.admin_service.dto.AdminUserUpdateAccountRequest;
import com.tlcn.admin_service.dto.AdminUserResponse;
import com.tlcn.admin_service.dto.AdminUpdateAccountRequest;
import com.tlcn.admin_service.dto.AdminUpdateProfileRequest;
import com.tlcn.admin_service.dto.ForgetPasswordRequest;
import com.tlcn.admin_service.dto.LoginRequest;
import com.tlcn.admin_service.dto.LoginResponse;
import com.tlcn.admin_service.dto.LogoutRequest;
import com.tlcn.admin_service.dto.RefreshTokenRequest;
import com.tlcn.admin_service.dto.ResetPasswordRequest;
import com.tlcn.admin_service.dto.ResponseDTO;
import com.tlcn.admin_service.enums.AdminUserStatus;
import com.tlcn.admin_service.service.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @PostMapping("/login")
    public ResponseEntity<ResponseDTO<LoginResponse>> login(@Valid @RequestBody LoginRequest loginRequest) {
        LoginResponse loginResponse = adminUserService.login(loginRequest);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Login successful", loginResponse));
    }

    @PostMapping("/logout")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<ResponseDTO<Void>> logout(@Valid @RequestBody LogoutRequest request) {
        adminUserService.logout(request.getAccessToken(), request.getRefreshToken());
        return ResponseEntity.ok(new ResponseDTO<>(true, "Logout successful", null));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ResponseDTO<LoginResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        LoginResponse loginResponse = adminUserService.refresh(request.getRefreshToken());
        return ResponseEntity.ok(new ResponseDTO<>(true, "Token refreshed successfully", loginResponse));
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ResponseDTO<AdminUserResponse>> createAdminUser(@Valid @RequestBody AdminUserCreateRequest createDTO) {
        AdminUserResponse adminUser = adminUserService.createAdminUser(createDTO);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ResponseDTO<>(true, "Admin user created successfully", adminUser));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ResponseDTO<AdminUserResponse>> getAdminUser(@PathVariable("id") Long id) {
        AdminUserResponse adminUser = adminUserService.getAdminUserById(id);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Admin user retrieved successfully", adminUser));
    }

    @GetMapping("/profile")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<ResponseDTO<AdminUserResponse>> getCurrentAdminUser() {
        AdminUserResponse adminUser = adminUserService.getCurrentAdminUser();
        return ResponseEntity.ok(new ResponseDTO<>(true, "Current admin user retrieved successfully", adminUser));
    }

    @PutMapping("/profile")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ResponseDTO<AdminUserResponse>> updateMyProfile(
            @Valid @RequestBody AdminUpdateProfileRequest request) {
        AdminUserResponse adminUser = adminUserService.updateMyProfile(request);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Profile updated successfully", adminUser));
    }

    @PutMapping("/account")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<ResponseDTO<AdminUserResponse>> updateMyAccount(
            @Valid @RequestBody AdminUpdateAccountRequest request) {
        AdminUserResponse adminUser = adminUserService.updateMyAccount(request);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Account updated successfully", adminUser));
    }

    @PutMapping("/{id}/profile")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ResponseDTO<AdminUserResponse>> updateAdminProfile(
            @PathVariable("id") Long id,
            @Valid @RequestBody AdminUpdateProfileRequest request) {
        AdminUserResponse adminUser = adminUserService.updateAdminProfile(id, request);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Admin profile updated successfully", adminUser));
    }

    @PutMapping("/{id}/account")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ResponseDTO<AdminUserResponse>> updateAdminAccount(
            @PathVariable("id") Long id,
            @Valid @RequestBody AdminUserUpdateAccountRequest request) {
        AdminUserResponse adminUser = adminUserService.updateAdminAccount(id, request);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Admin account updated successfully", adminUser));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ResponseDTO<Void>> deleteAdminUser(@PathVariable("id") Long id) {
        adminUserService.deleteAdminUser(id);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Admin user deleted successfully", null));
    }

    @GetMapping("/active-admins")
    public ResponseEntity<ResponseDTO<List<ActiveAdminResponse>>> getActiveAdmins() {
        return ResponseEntity.ok(new ResponseDTO<>(true, "Active admins retrieved", adminUserService.getActiveAdmins()));
    }

    /**
     * Internal lookup used by other services (e.g. communication-service) to resolve
     * the display name of an admin by their Keycloak id.
     */
    @GetMapping("/internal/{keycloakId}")
    public ResponseEntity<ResponseDTO<ActiveAdminResponse>> getAdminProfile(@PathVariable("keycloakId") String keycloakId) {
        ActiveAdminResponse profile = adminUserService.getAdminProfileByKeycloakId(keycloakId);
        if (profile == null) {
            return ResponseEntity.ok(new ResponseDTO<>(false, "Admin not found", null));
        }
        return ResponseEntity.ok(new ResponseDTO<>(true, "OK", profile));
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ResponseDTO<Page<AdminUserResponse>>> getAllAdminUsers(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "status", required = false) AdminUserStatus status,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "query", required = false) String query) {
        Pageable pageable = PageRequest.of(page, size);
        String effectiveSearch = (query != null && !query.isBlank()) ? query : search;
        Page<AdminUserResponse> adminUsers = adminUserService.getAllAdminUsers(pageable, status, effectiveSearch);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Admin users retrieved successfully", adminUsers));
    }

    /**
     * Forget password endpoint
     *
     * @param request Forget password request
     * @return ResponseDTO with success message
     */
    @PostMapping("/forget-password")
    public ResponseEntity<ResponseDTO<Void>> forgetPassword(@Valid @RequestBody ForgetPasswordRequest request) {
        adminUserService.forgetPassword(request);
        return ResponseEntity.ok(new ResponseDTO<>(true, "If the email exists in our system, you will receive a password reset link.", null));
    }

    /**
     * Reset password endpoint
     *
     * @param request Reset password request
     * @return ResponseDTO with success message
     */
    @PostMapping("/reset-password")
    public ResponseEntity<ResponseDTO<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        adminUserService.resetPassword(request);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Password reset successfully", null));
    }
}