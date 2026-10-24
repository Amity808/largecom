package org.ecom.largecom.dto;

import org.ecom.largecom.domain.Role;

import java.util.UUID;

public record AuthResponse (
        UUID tenantId,
        UUID userId,
        String businessName,
        String slug,
        String email,
        Role role,
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
){
}
