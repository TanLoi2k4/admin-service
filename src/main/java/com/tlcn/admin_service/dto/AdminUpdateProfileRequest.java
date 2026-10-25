package com.tlcn.admin_service.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminUpdateProfileRequest {
    @Size(min = 1, max = 50, message = "First name must be between 1 and 50 characters")
    private String firstName;

    @Size(min = 1, max = 50, message = "Last name must be between 1 and 50 characters")
    private String lastName;

    @Pattern(
            regexp = "^$|^(\\+?[1-9]\\d{1,14}|0\\d{9,10})$",
            message = "Phone number should be valid (e.g. 0901234567 or +84901234567)"
    )
    private String phoneNumber;
}
