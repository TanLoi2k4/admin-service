package com.tlcn.admin_service.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ActiveAdminResponse {
    private String keycloakId;
    private String fullName;
    private String email;

    public ActiveAdminResponse(String keycloakId, String fullName, String email) {
        this.keycloakId = keycloakId;
        this.fullName = fullName;
        this.email = email;
    }
}
