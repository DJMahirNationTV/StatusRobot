package com.djmahirnationtv.status.backend.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findFirstByOrderByIdAsc();

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from AppUser u where u.id = :id")
    Optional<AppUser> lockForSettingsUpdate(Long id);
    Optional<AppUser> findByEmail(String email);
    Optional<AppUser> findByProviderAndProviderId(String provider, String providerId);
}
