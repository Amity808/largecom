package org.ecom.largecom.dto;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;


//like payload requesting data
public record RegisterRequest(
        @NotBlank(message = "Business name is required")
        String businessName,

        @NotBlank(message = "Store subname is required")
        @Size(min = 3, max = 63, message = "Subdomain must be between 3 and 63 characters")
        @Pattern(regexp = "^[a-z0-9-]+$", message = "Subdomain can only contain lowercase letters, numbers, and hyphens")
        String slug,

        @NotBlank(message = "Full name is required")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters long")
        String password

) {

}
