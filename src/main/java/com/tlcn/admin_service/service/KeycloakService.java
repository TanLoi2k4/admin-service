package com.tlcn.admin_service.service;

import com.tlcn.admin_service.exception.KeycloakException;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Form;
import jakarta.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class KeycloakService {

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.resource}")
    private String clientId;

    @Value("${keycloak.credentials.secret}")
    private String clientSecret;

    @Value("${keycloak.auth-server-url}")
    private String serverUrl;

    @Autowired
    @Qualifier("keycloakAdmin")
    private Keycloak keycloak;

    /**
     * Create admin user in Keycloak
     */
    public String createAdminUser(String username, String email, String firstName, String lastName, String password) {
        log.debug("Creating admin user in Keycloak: username={}, email={}, firstName={}, lastName={}",
                    username, email, firstName, lastName);

        try {
            // 1. Create user representation
            UserRepresentation user = new UserRepresentation();
            user.setUsername(username);
            user.setEmail(email);
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setEnabled(true);
            user.setEmailVerified(true);

            // 2. Set password credentials
            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(password);
            credential.setTemporary(false);
            user.setCredentials(Collections.singletonList(credential));

            // 3. Create user in Keycloak
            UsersResource usersResource = keycloak.realm(realm).users();
            Response response = usersResource.create(user);

            if (response.getStatus() != 201) {
                throw KeycloakException.failedToCreateUser("HTTP " + response.getStatus());
            }

            // 4. Get user ID from location header
            String userId = getUserIdFromResponse(response);
            log.info("Admin user created in Keycloak with ID: {}", userId);

            // 5. Assign ADMIN role
            assignAdminRole(userId);

            response.close();
            return userId;

        } catch (Exception e) {
            log.error("Failed to create admin user in Keycloak: {}", e.getMessage(), e);
            throw KeycloakException.failedToCreateUser(e.getMessage());
        }
    }

    /**
     * Assign ADMIN role to user
     */
    private void assignAdminRole(String userId) {
        try {
            // Get ADMIN role
            RoleRepresentation adminRole = keycloak.realm(realm).roles().get("ADMIN").toRepresentation();

            // Assign role to user
            keycloak.realm(realm).users().get(userId).roles().realmLevel().add(Collections.singletonList(adminRole));

            log.info("ADMIN role assigned to user: {}", userId);
        } catch (Exception e) {
            log.warn("Failed to assign ADMIN role to user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Update admin user in Keycloak
     */
    public void updateAdminUser(String keycloakId, String username, String email, String firstName, String lastName) {
        log.debug("Updating admin user in Keycloak: keycloakId={}, username={}, email={}, firstName={}, lastName={}",
                    keycloakId, username, email, firstName, lastName);

        try {
            UserRepresentation user = keycloak.realm(realm).users().get(keycloakId).toRepresentation();
            user.setUsername(username);
            user.setEmail(email);
            user.setFirstName(firstName);
            user.setLastName(lastName);

            keycloak.realm(realm).users().get(keycloakId).update(user);
            log.info("Admin user updated in Keycloak: {}", keycloakId);

        } catch (Exception e) {
            log.error("Failed to update admin user in Keycloak: {}", e.getMessage(), e);
            throw new KeycloakException("Failed to update admin user: " + e.getMessage(), e);
        }
    }

    /**
     * Delete admin user from Keycloak
     */
    public void deleteAdminUser(String keycloakId) {
        log.debug("Deleting admin user from Keycloak: keycloakId={}", keycloakId);

        try {
            Response response = keycloak.realm(realm).users().delete(keycloakId);

            if (response.getStatus() != 204) {
                throw KeycloakException.failedToDeleteUser("HTTP " + response.getStatus());
            }

            log.info("Admin user deleted from Keycloak: {}", keycloakId);
            response.close();

        } catch (Exception e) {
            log.error("Failed to delete admin user from Keycloak: {}", e.getMessage(), e);
            throw KeycloakException.failedToDeleteUser(e.getMessage());
        }
    }

    /**
     * Set admin user enabled status in Keycloak
     */
    public void setAdminUserEnabled(String keycloakId, boolean enabled) {
        log.debug("Setting admin user enabled status in Keycloak: keycloakId={}, enabled={}", keycloakId, enabled);

        try {
            UserRepresentation user = keycloak.realm(realm).users().get(keycloakId).toRepresentation();
            user.setEnabled(enabled);

            keycloak.realm(realm).users().get(keycloakId).update(user);
            log.info("Admin user enabled status updated in Keycloak: {} -> {}", keycloakId, enabled);

        } catch (Exception e) {
            log.error("Failed to set admin user enabled status in Keycloak: {}", e.getMessage(), e);
            throw new KeycloakException("Failed to update user status: " + e.getMessage(), e);
        }
    }
    public void changePassword(String keycloakId, String newPassword) {
        log.debug("Changing password for user in Keycloak: keycloakId={}", keycloakId);

        try {
            // Create credential representation
            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(newPassword);
            credential.setTemporary(false);

            // Update password
            keycloak.realm(realm).users().get(keycloakId).resetPassword(credential);

            log.info("Password changed successfully for user: {}", keycloakId);

        } catch (Exception e) {
            log.error("Failed to change password for user in Keycloak: {}", e.getMessage(), e);
            throw KeycloakException.failedToAssignRole(keycloakId, e.getMessage());
        }
    }

    /**
     * Get user token (for login)
     */
    public AccessTokenResponse getUserToken(String username, String password) {
        log.debug("Getting token for admin user: {}", username);

        try {
            Client client = ClientBuilder.newClient();

            Form form = new Form();
            form.param("grant_type", "password");
            form.param("client_id", clientId);
            form.param("client_secret", clientSecret);
            form.param("username", username);
            form.param("password", password);

            Response response = client.target(serverUrl + "/realms/" + realm + "/protocol/openid-connect/token")
                    .request()
                    .post(Entity.form(form));

            if (response.getStatus() != 200) {
                throw new KeycloakException("Failed to get token: " + response.getStatus());
            }

            AccessTokenResponse tokenResponse = response.readEntity(AccessTokenResponse.class);
            client.close();

            log.info("Token obtained for admin user: {}", username);
            return tokenResponse;

        } catch (Exception e) {
            log.error("Failed to get token for admin user: {}", e.getMessage(), e);
            throw new KeycloakException("Failed to authenticate admin user: " + e.getMessage(), e);
        }
    }

    /**
     * Extract user ID from Keycloak response
     */
    private String getUserIdFromResponse(Response response) {
        String location = response.getHeaderString("Location");
        if (location != null) {
            String[] parts = location.split("/");
            return parts[parts.length - 1];
        }
        throw KeycloakException.invalidUser("Unable to extract user ID from response");
    }

    /**
     * Check if user exists in Keycloak
     */
    public boolean userExists(String username) {
        try {
            List<UserRepresentation> users = keycloak.realm(realm).users().search(username, true);
            return !users.isEmpty();
        } catch (Exception e) {
            log.warn("Error checking if user exists: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check if email exists in Keycloak
     */
    public boolean emailExists(String email) {
        try {
            List<UserRepresentation> users = keycloak.realm(realm).users().search(null, null, null, email, 0, 1);
            return !users.isEmpty();
        } catch (Exception e) {
            log.warn("Error checking if email exists: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get user info from Keycloak by keycloakId
     */
    public UserRepresentation getUserInfo(String keycloakId) {
        try {
            UserRepresentation user = keycloak.realm(realm).users().get(keycloakId).toRepresentation();
            return user;
        } catch (Exception e) {
            log.error("Failed to get user info from Keycloak: {}", e.getMessage(), e);
            throw KeycloakException.invalidUser(keycloakId);
        }
    }

    /**
     * Get user roles from Keycloak
     */
    public List<String> getUserRoles(String keycloakId) {
        try {
            List<RoleRepresentation> roles = keycloak.realm(realm).users().get(keycloakId)
                    .roles().realmLevel().listAll();
            return roles.stream()
                    .map(RoleRepresentation::getName)
                    .collect(java.util.stream.Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to get user roles from Keycloak: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Refresh access token using refresh token grant.
     */
    public AccessTokenResponse refreshToken(String refreshToken) {
        log.debug("Refreshing access token");
        try {
            Client client = ClientBuilder.newClient();

            Form form = new Form();
            form.param("grant_type", "refresh_token");
            form.param("client_id", clientId);
            form.param("client_secret", clientSecret);
            form.param("refresh_token", refreshToken);

            Response response = client.target(serverUrl + "/realms/" + realm + "/protocol/openid-connect/token")
                    .request()
                    .post(Entity.form(form));

            if (response.getStatus() != 200) {
                String errorMessage = response.readEntity(String.class);
                log.warn("Refresh token failed: status={}, message={}", response.getStatus(), errorMessage);
                throw new KeycloakException("Refresh token failed: " + errorMessage);
            }

            AccessTokenResponse tokenResponse = response.readEntity(AccessTokenResponse.class);
            response.close();
            client.close();

            log.info("Access token refreshed successfully");
            return tokenResponse;
        } catch (KeycloakException e) {
            throw e;
        } catch (Exception e) {
            log.error("Refresh token failed: {}", e.getMessage(), e);
            throw new KeycloakException("Refresh token failed: " + e.getMessage());
        }
    }

    /**
     * Logout user from Keycloak using refresh token
     */
    public void logoutUser(String accessToken, String refreshToken) {
        // For consistency with other services, but only refreshToken is actually used
        logoutUser(refreshToken);
    }

    /**
     * Logout user from Keycloak using refresh token
     */
    public void logoutUser(String refreshToken) {
        log.debug("Logging out user with refresh token");
        try {
            Client client = ClientBuilder.newClient();
            String logoutUrl = serverUrl + "/realms/" + realm + "/protocol/openid-connect/logout";

            Form form = new Form();
            form.param("client_id", clientId);
            form.param("client_secret", clientSecret);
            form.param("refresh_token", refreshToken);

            Response response = client.target(logoutUrl)
                    .request()
                    .post(Entity.form(form));

            if (response.getStatus() != 204) {
                String errorMessage = response.readEntity(String.class);
                log.error("Failed to logout user: status={}, message={}", response.getStatus(), errorMessage);
                throw KeycloakException.logoutFailed(errorMessage);
            }

            response.close();
            client.close();
            log.debug("User logged out successfully");

        } catch (Exception e) {
            log.error("Error during Keycloak logout: {}", e.getMessage(), e);
            throw KeycloakException.logoutFailed(e.getMessage());
        }
    }
}