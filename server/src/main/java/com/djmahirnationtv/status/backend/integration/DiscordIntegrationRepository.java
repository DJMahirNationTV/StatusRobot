package com.djmahirnationtv.status.backend.integration;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface DiscordIntegrationRepository extends JpaRepository<DiscordIntegration, Long> {
    List<DiscordIntegration> findByOwnerIdOrderByIdDesc(Long ownerId);

    @Query("select i from Monitor m join m.integrations i where m.id = :monitorId and i.ownerId = :ownerId")
    List<DiscordIntegration> findForMonitor(Long monitorId, Long ownerId);
}
