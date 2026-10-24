package org.ecom.largecom.Repository;

import org.ecom.largecom.domain.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {
//    Optional<Tenant> findById(UUID id);
    Optional<Tenant> findBySlug(String slug);
    boolean existsBySlug(String slug);
}
