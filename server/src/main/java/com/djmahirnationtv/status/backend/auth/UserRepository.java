package com.djmahirnationtv.status.backend.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findFirstByOrderByIdAsc();

    @Query(value = "select * from app_users where id = :id for update", nativeQuery = true)
    Optional<AppUser> lockForSettingsUpdate(@Param("id") Long id);
    Optional<AppUser> findByEmail(String email);
    Optional<AppUser> findByProviderAndProviderId(String provider, String providerId);
}
