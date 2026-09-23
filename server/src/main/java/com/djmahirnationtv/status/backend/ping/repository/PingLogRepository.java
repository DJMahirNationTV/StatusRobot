package com.djmahirnationtv.status.backend.ping.repository;
import com.djmahirnationtv.status.backend.ping.model.PingLog;
import org.springframework.stereotype.Repository;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.time.Instant;

@Repository
public interface PingLogRepository extends MongoRepository<PingLog, String> {
    List<PingLog> findByMonitorIdOrderByTimestampDesc(Long monitorId, Pageable pageable);

    List<PingLog> findByMonitorIdAndTimestampAfterOrderByTimestampAsc(Long monitorId, Instant after);
    
    void deleteByMonitorId(Long monitorId);
}