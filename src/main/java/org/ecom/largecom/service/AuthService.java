package org.ecom.largecom.service;


import jakarta.transaction.Transactional;
import org.ecom.largecom.Repository.RefreshTokenRepository;
import org.ecom.largecom.Repository.UserRepository;
import org.ecom.largecom.Repository.TenantRepository;
import org.ecom.largecom.domain.Role;
import org.ecom.largecom.domain.Tenant;
import org.ecom.largecom.domain.User;
import org.ecom.largecom.dto.AuthResponse;
import org.ecom.largecom.dto.LoginRequest;
import org.ecom.largecom.dto.RegisterRequest;
import org.ecom.largecom.exception.DuplicateResourceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;

    public AuthService(TenantRepository tenantRepository, UserRepository userRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService,  RefreshTokenRepository refreshTokenRepository) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional
    public AuthResponse resgister(RegisterRequest request) {
        String normalizedSlug = request.slug().trim().toLowerCase();
        String normalizedEmail = request.email().trim().toLowerCase();

//        check if they exit
        if (tenantRepository.existsBySlug(normalizedSlug)) {
            throw new DuplicateResourceException("Store subdomain '" + normalizedSlug + "' is already taken");
        }

//        create and persist the tenant (merchant store)
        Tenant tenant = new Tenant(
                normalizedSlug, request.businessName().trim(), normalizedEmail
        );
        tenant = tenantRepository.save(tenant);

        String hashedPassword = passwordEncoder.encode(request.password());

        User owner = new User(
                tenant, normalizedEmail, hashedPassword,request.fullName().trim(),
                Role.owner
        );
        owner = userRepository.save(owner);
        String accessToken = jwtService.generateAccessToken(owner);
        return new AuthResponse(
                tenant.getId(),
                owner.getId(),
                tenant.getBusinessName(),
                tenant.getSlug(),
                owner.getEmail(),
                owner.getRole(),
                accessToken,
                "Bearer",
                jwtService.getExpirationSeconds()
        );
    }


    @Transactional
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

//        find user by email
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new
                        BadCredentialsException("Invalid email or password"));
//        verify password with argon2
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid password");
        }

//        update last login timestamp
        user.setLastLoginAt(java.time.Instant.now());
        userRepository.save(user);

//        generate jwt access token
        String accessToken = jwtService.generateAccessToken(user);

        return new AuthResponse(
                user.getTenant().getId(),
                user.getId(),
                user.getTenant().getBusinessName(),
                user.getTenant().getSlug(),
                user.getEmail(), user.getRole(),
                accessToken,
                "Bearer",
                jwtService.getExpirationSeconds()
        );
    }
}
